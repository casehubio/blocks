package io.casehub.blocks.agentic.termination;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeConvergenceTest {

    @Test
    void convergesWhenJudgeReturnsSuccess() {
        var judge = AgentRef.external((List<AgentResult> results) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "CONVERGED")));

        var termination = new JudgeConvergence<String>(judge, 10);
        var context = new TerminationContext<>("state", 2, Duration.ZERO, List.of(
                AgentResult.success(null, "position A"),
                AgentResult.success(null, "position B")));

        var decision = termination.evaluate(context).await().indefinitely();
        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
        assertThat(((TerminationDecision.Complete) decision).result()).isEqualTo("CONVERGED");
    }

    @Test
    void continuesWhenJudgeReturnsDeclined() {
        var judge = AgentRef.external((List<AgentResult> results) ->
                CompletableFuture.completedFuture(AgentResult.declined(null, "still diverging")));

        var termination = new JudgeConvergence<String>(judge, 10);
        var context = new TerminationContext<>("state", 1, Duration.ZERO, List.of());

        var decision = termination.evaluate(context).await().indefinitely();
        assertThat(decision).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void safetyCapTerminatesAtMaxIterations() {
        var judge = AgentRef.external((List<AgentResult> results) ->
                CompletableFuture.completedFuture(AgentResult.declined(null, "never converges")));

        var termination = new JudgeConvergence<String>(judge, 3);
        var context = new TerminationContext<>("state", 3, Duration.ZERO, List.of());

        var decision = termination.evaluate(context).await().indefinitely();
        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void convenienceConstructorRejectsNonExternalAgent() {
        var worker = io.casehub.worker.api.Worker.builder()
                .name("judge").capabilityName("judge")
                .function(io.casehub.worker.api.WorkerFunction.NONE).build();
        assertThatThrownBy(() -> new JudgeConvergence<>(AgentRef.worker(worker), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
