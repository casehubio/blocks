package io.casehub.blocks.agentic.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.judgment.JudgmentDecision;
import io.casehub.blocks.agentic.judgment.JudgmentPhase;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExecutionModelJudgmentTest {

    @Test
    void nullJudgment_loopBehavesAsToday() {
        var agent = AgentRef.external("a", ctx -> CompletableFuture.completedFuture(
            AgentResult.success(null, "done")));
        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(1),
            () -> List.of(new RoutingCandidate(agent, null)),
            FailurePolicy.defaults(),
            List.of(),
            "test",
            PatternType.SUPERVISOR);

        assertThat(model.judgment()).isNull();
        var driver = new OrchestratedDriver<>();
        var result = driver.execute(model, "ctx").await().indefinitely();
        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void approvedJudgment_proceedsToTermination() {
        var agent = AgentRef.external("a", ctx -> CompletableFuture.completedFuture(
            AgentResult.success(null, "done")));
        JudgmentPhase<Object> phase = ctx ->
            new JudgmentDecision.Approved("ok", List.of(), CallerIdentity.of("test", "llm"));

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(1),
            () -> List.of(new RoutingCandidate(agent, null)),
            FailurePolicy.defaults(),
            List.of(),
            "test",
            PatternType.SUPERVISOR,
            null,
            phase);

        var driver = new OrchestratedDriver<>();
        var result = driver.execute(model, "ctx").await().indefinitely();
        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void rejectedJudgment_reIteratesWithFeedback() {
        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external("a", ctx -> CompletableFuture.completedFuture(
            AgentResult.success(null, "attempt-" + callCount.incrementAndGet())));

        var judgmentCalls = new AtomicInteger(0);
        var capturedFeedback = new ArrayList<String>();
        JudgmentPhase<Object> phase = ctx -> {
            int call = judgmentCalls.incrementAndGet();
            capturedFeedback.add(ctx.previousFeedback());
            if (call == 1) {
                return new JudgmentDecision.Rejected("needs more detail", List.of(),
                    CallerIdentity.of("test", "llm"));
            }
            return new JudgmentDecision.Approved("ok", List.of(),
                CallerIdentity.of("test", "llm"));
        };

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(2),
            () -> List.of(new RoutingCandidate(agent, null)),
            FailurePolicy.defaults(),
            List.of(),
            "test",
            PatternType.SUPERVISOR,
            null,
            phase);

        var driver = new OrchestratedDriver<>();
        var result = driver.execute(model, "ctx").await().indefinitely();
        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(callCount.get()).isEqualTo(2);
        assertThat(judgmentCalls.get()).isEqualTo(2);
        assertThat(capturedFeedback.get(0)).isNull();
        assertThat(capturedFeedback.get(1)).isEqualTo("needs more detail");
    }

    @Test
    void escalatedJudgment_escalatesExecution() {
        var agent = AgentRef.external("a", ctx -> CompletableFuture.completedFuture(
            AgentResult.success(null, "done")));
        JudgmentPhase<Object> phase = ctx ->
            new JudgmentDecision.Escalated("beyond expertise", CallerIdentity.of("test", "llm"));

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(5),
            () -> List.of(new RoutingCandidate(agent, null)),
            FailurePolicy.defaults(),
            List.of(),
            "test",
            PatternType.SUPERVISOR,
            null,
            phase);

        var driver = new OrchestratedDriver<>();
        var result = driver.execute(model, "ctx").await().indefinitely();
        assertThat(result).isInstanceOf(ExecutionResult.Escalated.class);
    }

    @Test
    void listener_receivesJudgmentCallbacks() {
        var agent = AgentRef.external("a", ctx -> CompletableFuture.completedFuture(
            AgentResult.success(null, "done")));
        var decisions = new ArrayList<JudgmentDecision>();
        JudgmentPhase<Object> phase = ctx ->
            new JudgmentDecision.Approved("ok", List.of(), CallerIdentity.of("test", "llm"));

        ExecutionEventListener listener = new ExecutionEventListener() {
            @Override
            public void onJudgment(JudgmentDecision decision) {
                decisions.add(decision);
            }
        };

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            new MaxIterationsTermination<>(1),
            () -> List.of(new RoutingCandidate(agent, null)),
            FailurePolicy.defaults(),
            List.of(listener),
            "test",
            PatternType.SUPERVISOR,
            null,
            phase);

        var driver = new OrchestratedDriver<>();
        driver.execute(model, "ctx").await().indefinitely();
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0)).isInstanceOf(JudgmentDecision.Approved.class);
    }
}
