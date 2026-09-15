package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JudgmentPolicy<T> implements JudgmentPhase<T> {

    private final JudgmentTrigger<T> trigger;
    private final CallerStrategy callerStrategy;
    private final JudgmentVerifier verifier;
    private final RetryPolicy retryPolicy;
    private final JudgmentDispatcher dispatcher;
    private final Duration dispatchTimeout;

    private JudgmentPolicy(Builder<T> b) {
        this.trigger = Objects.requireNonNull(b.trigger, "trigger");
        this.callerStrategy = Objects.requireNonNull(b.callerStrategy, "callerStrategy");
        this.verifier = Objects.requireNonNull(b.verifier, "verifier");
        this.retryPolicy = Objects.requireNonNull(b.retryPolicy, "retryPolicy");
        this.dispatcher = Objects.requireNonNull(b.dispatcher, "dispatcher");
        this.dispatchTimeout = b.dispatchTimeout != null ? b.dispatchTimeout : Duration.ofMinutes(5);
    }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    @Override
    public JudgmentDecision evaluate(JudgmentContext<T> context) {
        if (!trigger.shouldYield(context)) {
            return new JudgmentDecision.Approved("skipped", List.of(),
                    CallerIdentity.of("system", "system"));
        }

        return switch (callerStrategy) {
            case CallerStrategy.Single single -> evaluateSingle(single, context);
            case CallerStrategy.FanOut fanOut -> evaluateFanOut(fanOut, context);
            case CallerStrategy.EscalationChain chain -> evaluateChain(chain, context);
        };
    }

    private JudgmentDecision evaluateSingle(CallerStrategy.Single single,
                                             JudgmentContext<T> context) {
        return dispatchAndVerify(single.caller(), context);
    }

    private JudgmentDecision evaluateFanOut(CallerStrategy.FanOut fanOut,
                                             JudgmentContext<T> context) {
        String feedback = context.previousFeedback();
        for (int attempt = 0; attempt <= retryPolicy.maxRetries(); attempt++) {
            var responses = new ArrayList<JudgmentResponse>();
            for (var caller : fanOut.callers()) {
                var response = dispatcher.dispatch(
                        new JudgmentDispatchRequest(context, caller, feedback));
                responses.add(response);
            }

            var agreement = fanOut.agreementPolicy().evaluate(responses);
            if (agreement instanceof AgreementResult.Disagreed disagreed) {
                feedback = disagreed.reason();
                continue;
            }

            var agreed = ((AgreementResult.Agreed) agreement).selectedResponse();
            var verificationResult = verify(agreed);
            if (verificationResult instanceof VerificationResult.Accepted) {
                return new JudgmentDecision.Approved(agreed.decision(),
                        agreed.evidence(), agreed.callerIdentity());
            }
            feedback = extractFeedback(verificationResult);
        }
        return applyExhaustion();
    }

    private JudgmentDecision evaluateChain(CallerStrategy.EscalationChain chain,
                                            JudgmentContext<T> context) {
        for (var caller : chain.callers()) {
            var decision = dispatchAndVerify(caller, context);
            if (decision instanceof JudgmentDecision.Approved) {
                return decision;
            }
        }
        return applyExhaustion();
    }

    private JudgmentDecision dispatchAndVerify(CallerRef caller, JudgmentContext<T> context) {
        String feedback = context.previousFeedback();
        for (int attempt = 0; attempt <= retryPolicy.maxRetries(); attempt++) {
            var response = dispatcher.dispatch(
                    new JudgmentDispatchRequest(context, caller, feedback));

            var verificationResult = verify(response);
            if (verificationResult instanceof VerificationResult.Accepted) {
                return new JudgmentDecision.Approved(response.decision(),
                        response.evidence(), response.callerIdentity());
            }

            var failurePolicy = resolveFailurePolicy();
            if (failurePolicy == VerifierFailurePolicy.FAIL) {
                return new JudgmentDecision.Rejected(extractFeedback(verificationResult),
                        response.evidence(), response.callerIdentity());
            }
            if (failurePolicy == VerifierFailurePolicy.ESCALATE) {
                break;
            }
            feedback = extractFeedback(verificationResult);
        }
        return applyExhaustion();
    }

    private VerificationResult verify(JudgmentResponse response) {
        var verificationCtx = new VerificationContext(
                null, "", "", null, Map.of(), null,
                String.valueOf(response.decision()), response.evidence(),
                response.callerIdentity(), null);
        return verifier.verify(verificationCtx);
    }

    private VerifierFailurePolicy resolveFailurePolicy() {
        if (verifier instanceof CompositeVerifier composite) {
            return composite.mostRestrictivePolicy();
        }
        return VerifierFailurePolicy.RETRY_WITH_FEEDBACK;
    }

    private JudgmentDecision applyExhaustion() {
        return switch (retryPolicy.exhaustionPolicy()) {
            case FAIL -> new JudgmentDecision.Rejected("Judgment exhausted retries",
                    List.of(), CallerIdentity.of("system", "system"));
            case ESCALATE_NEXT_CALLER -> new JudgmentDecision.Rejected(
                    callerStrategy instanceof CallerStrategy.EscalationChain
                            ? "Escalation chain exhausted"
                            : "Escalation requested but no chain configured",
                    List.of(), CallerIdentity.of("system", "system"));
            case TERMINATE_PATTERN -> new JudgmentDecision.Escalated(
                    "Judgment terminated pattern", CallerIdentity.of("system", "system"));
        };
    }

    private String extractFeedback(VerificationResult result) {
        return switch (result) {
            case VerificationResult.Rejected r -> r.reason();
            case VerificationResult.InsufficientEvidence ie -> ie.feedback();
            case VerificationResult.TrustTooLow t ->
                    "Trust too low: required=" + t.requiredLevel() + " actual=" + t.actualLevel();
            case VerificationResult.Accepted a -> "";
        };
    }

    public static final class Builder<T> {
        private JudgmentTrigger<T> trigger;
        private CallerStrategy callerStrategy;
        private JudgmentVerifier verifier;
        private RetryPolicy retryPolicy;
        private JudgmentDispatcher dispatcher;
        private Duration dispatchTimeout;

        public Builder<T> trigger(JudgmentTrigger<T> trigger) { this.trigger = trigger; return this; }
        public Builder<T> caller(CallerStrategy callerStrategy) { this.callerStrategy = callerStrategy; return this; }
        public Builder<T> verifier(JudgmentVerifier verifier) { this.verifier = verifier; return this; }
        public Builder<T> retryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; return this; }
        public Builder<T> dispatcher(JudgmentDispatcher dispatcher) { this.dispatcher = dispatcher; return this; }
        public Builder<T> dispatchTimeout(Duration timeout) { this.dispatchTimeout = timeout; return this; }

        public JudgmentPolicy<T> build() { return new JudgmentPolicy<>(this); }
    }
}
