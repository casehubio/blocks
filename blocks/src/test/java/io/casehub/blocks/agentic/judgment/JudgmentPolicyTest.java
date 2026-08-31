package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentPolicyTest {

    private static JudgmentContext<String> ctx(int iteration) {
        return new JudgmentContext<>("state", List.of(), null, iteration, null);
    }

    private static JudgmentDispatcher approvingDispatcher() {
        return req -> new JudgmentResponse("approved", List.of(),
                CallerIdentity.of("test", "agent"));
    }

    @Test
    void triggerReturnsFalse_returnsApprovedSkipped() {
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new NeverYield<>())
                .caller(CallerStrategy.single())
                .verifier(new NoOpVerifier())
                .dispatcher(approvingDispatcher())
                .retryPolicy(RetryPolicy.defaults())
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
    }

    @Test
    void singleCaller_dispatchesAndVerifies() {
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new AlwaysYield<>())
                .caller(CallerStrategy.single(CallerRef.agent("j", null)))
                .verifier(new NoOpVerifier())
                .dispatcher(approvingDispatcher())
                .retryPolicy(RetryPolicy.defaults())
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
        assertThat(((JudgmentDecision.Approved) decision).result()).isEqualTo("approved");
    }

    @Test
    void verificationRejected_retriesWithFeedback() {
        var callCount = new AtomicInteger(0);
        JudgmentDispatcher dispatcher = req -> {
            callCount.incrementAndGet();
            return new JudgmentResponse("result", List.of(), CallerIdentity.of("t", "a"));
        };
        var verifyCount = new AtomicInteger(0);
        var rejectThenAccept = new io.casehub.api.spi.judgment.JudgmentVerifier() {
            @Override public String id() { return "test"; }
            @Override public VerificationResult verify(VerificationContext ctx) {
                return verifyCount.incrementAndGet() <= 2
                        ? new VerificationResult.Rejected("not yet")
                        : new VerificationResult.Accepted();
            }
        };
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new AlwaysYield<>())
                .caller(CallerStrategy.single(CallerRef.agent("j", null)))
                .verifier(rejectThenAccept)
                .dispatcher(dispatcher)
                .retryPolicy(new RetryPolicy(3, ExhaustionPolicy.FAIL))
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void retriesExhausted_returnRejected() {
        var alwaysReject = new io.casehub.api.spi.judgment.JudgmentVerifier() {
            @Override public String id() { return "reject"; }
            @Override public VerificationResult verify(VerificationContext ctx) {
                return new VerificationResult.Rejected("bad");
            }
        };
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new AlwaysYield<>())
                .caller(CallerStrategy.single(CallerRef.agent("j", null)))
                .verifier(alwaysReject)
                .dispatcher(approvingDispatcher())
                .retryPolicy(new RetryPolicy(2, ExhaustionPolicy.FAIL))
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Rejected.class);
    }

    @Test
    void terminatePattern_exhaustion_returnsEscalated() {
        var alwaysReject = new io.casehub.api.spi.judgment.JudgmentVerifier() {
            @Override public String id() { return "reject"; }
            @Override public VerificationResult verify(VerificationContext ctx) {
                return new VerificationResult.Rejected("bad");
            }
        };
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new AlwaysYield<>())
                .caller(CallerStrategy.single(CallerRef.agent("j", null)))
                .verifier(alwaysReject)
                .dispatcher(approvingDispatcher())
                .retryPolicy(new RetryPolicy(0, ExhaustionPolicy.TERMINATE_PATTERN))
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Escalated.class);
    }

    @Test
    void fanOut_allAgree_returnsApproved() {
        var callCount = new AtomicInteger(0);
        JudgmentDispatcher dispatcher = req -> {
            callCount.incrementAndGet();
            return new JudgmentResponse("yes", List.of(), CallerIdentity.of("t", "a"));
        };
        var callers = List.of(CallerRef.agent("a", null), CallerRef.agent("b", null));
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new AlwaysYield<>())
                .caller(CallerStrategy.fanOut(callers, ConsensusAgreement.unanimous()))
                .verifier(new NoOpVerifier())
                .dispatcher(dispatcher)
                .retryPolicy(RetryPolicy.defaults())
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void fanOut_disagreement_retriesThenRejects() {
        var round = new AtomicInteger(0);
        JudgmentDispatcher dispatcher = req -> {
            int r = round.incrementAndGet();
            String answer = r % 2 == 0 ? "no" : "yes";
            return new JudgmentResponse(answer, List.of(), CallerIdentity.of("t", "a"));
        };
        var callers = List.of(CallerRef.agent("a", null), CallerRef.agent("b", null));
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new AlwaysYield<>())
                .caller(CallerStrategy.fanOut(callers, ConsensusAgreement.unanimous()))
                .verifier(new NoOpVerifier())
                .dispatcher(dispatcher)
                .retryPolicy(new RetryPolicy(1, ExhaustionPolicy.FAIL))
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Rejected.class);
    }

    @Test
    void escalationChain_firstAccepted_returnsApproved() {
        JudgmentDispatcher dispatcher = req -> new JudgmentResponse(
                "ok", List.of(), CallerIdentity.of(req.caller().id(), "agent"));
        var callers = List.of(CallerRef.agent("a", null), CallerRef.agent("b", null));
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new AlwaysYield<>())
                .caller(new CallerStrategy.EscalationChain(callers))
                .verifier(new NoOpVerifier())
                .dispatcher(dispatcher)
                .retryPolicy(RetryPolicy.defaults())
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
    }
}
