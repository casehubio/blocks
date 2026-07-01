package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.termination.JudgeConvergence;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static io.casehub.blocks.agentic.pattern.Patterns.debate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebateBuilderTest {

    @Test
    void executesDebateRounds() {
        var critic = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "critique")));
        var advocate = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "support")));

        var result = debate()
                .debaters(critic, advocate)
                .maxRounds(3)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void judgeTerminatesDebateOnConvergence() {
        var log = new ArrayList<String>();
        var callCount = new AtomicInteger();
        var optimist = AgentRef.external((Object in) -> {
            log.add("optimist");
            return CompletableFuture.completedFuture(AgentResult.success(null, "approve"));
        });
        var pessimist = AgentRef.external((Object in) -> {
            log.add("pessimist");
            return CompletableFuture.completedFuture(AgentResult.success(null, "approve"));
        });
        // Judge declines on first call (let both debaters run), converges on second
        var judge = AgentRef.external((List<AgentResult> results) -> {
            log.add("judge");
            if (callCount.incrementAndGet() < 2) {
                return CompletableFuture.completedFuture(AgentResult.declined(null, "not yet"));
            }
            return CompletableFuture.completedFuture(AgentResult.success(null, "CONVERGED"));
        });

        var result = Patterns.<Object>debate()
                .debaters(optimist, pessimist)
                .judge(judge)
                .maxRounds(10)
                .task("review PR")
                .execute("review")
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(((ExecutionResult.Completed) result).result()).isEqualTo("CONVERGED");
        assertThat(log).contains("optimist", "pessimist", "judge");
    }

    @Test
    void judgeAndConvergenceAreMutuallyExclusive() {
        var judge = AgentRef.external((List<AgentResult> r) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        assertThatThrownBy(() -> Patterns.debate()
                .judge(judge)
                .convergence(new MaxIterationsTermination<>(5))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void maxRoundsAfterConvergenceClearsFlag() {
        var debater = AgentRef.external((Object in) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var judge = AgentRef.external((List<AgentResult> r) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        // Should not throw — maxRounds clears convergenceExplicitlySet
        var model = Patterns.debate()
                .debaters(debater)
                .convergence(new MaxIterationsTermination<>(5))
                .maxRounds(10)
                .judge(judge)
                .build();
        assertThat(model.termination()).isInstanceOf(JudgeConvergence.class);
    }
}
