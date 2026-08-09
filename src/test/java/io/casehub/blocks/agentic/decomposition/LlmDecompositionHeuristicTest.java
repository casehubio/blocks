package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDecompositionHeuristicTest {

    private static AgentRef agent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static PrimitiveTask<String> leaf(String id) {
        return new PrimitiveTask<>(id, Instant.now(), id, agent(), null, null);
    }

    private static AgenticDecompositionContext<String> ctx(String state) {
        return new AgenticDecompositionContext<>(state, List.of(), 0);
    }

    private static AgentProvider providerReturning(String text) {
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(text)));
        return provider;
    }

    private static AgentProvider failingProvider() {
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));
        return provider;
    }

    @Test
    void parsesWellFormedResponse() {
        var json = """
                [{"method": 1, "score": 0.8}, {"method": 2, "score": 0.3}]
                """;
        var method1 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"), leaf("b"))), null);
        var method2 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("c"))), null);

        var heuristic = new LlmDecompositionHeuristic<String>(providerReturning(json));
        var methods = List.of(method1, method2);
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx("state"))
                .await().indefinitely();

        assertThat(scored).hasSize(2);
        assertThat(scored.get(0).score()).isEqualTo(0.8);
        assertThat(scored.get(1).score()).isEqualTo(0.3);
    }

    @Test
    void parsesMarkdownFencedResponse() {
        var json = """
                ```json
                [{"method": 1, "score": 0.6}]
                ```
                """;
        var method = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"))), null);

        var heuristic = new LlmDecompositionHeuristic<String>(providerReturning(json));
        var methods = List.of(method);
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx("state"))
                .await().indefinitely();

        assertThat(scored).hasSize(1);
        assertThat(scored.get(0).score()).isEqualTo(0.6);
    }

    @Test
    void llmFailure_returnsEqualScores() {
        var method1 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"))), null);
        var method2 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("b"))), null);

        var heuristic = new LlmDecompositionHeuristic<String>(failingProvider());
        var methods = List.of(method1, method2);
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx("state"))
                .await().indefinitely();

        assertThat(scored).hasSize(2);
        assertThat(scored.get(0).score()).isEqualTo(0.0);
        assertThat(scored.get(1).score()).isEqualTo(0.0);
    }

    @Test
    void stateRendererIntegration() {
        var promptCapture = new AtomicReference<String>();
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
            var config = invocation.getArgument(0, AgentSessionConfig.class);
            promptCapture.set(config.userPrompt());
            return Multi.createFrom().item(new AgentEvent.TextDelta(
                    "[{\"method\": 1, \"score\": 0.5}]"));
        });

        var method = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"))), null);
        var heuristic = new LlmDecompositionHeuristic<String>(provider, s -> "RENDERED:" + s);
        var methods = List.of(method);
        heuristic.evaluate(new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "goal", methods),
                methods, ctx("my-state")).await().indefinitely();

        assertThat(promptCapture.get()).contains("RENDERED:my-state");
        assertThat(promptCapture.get()).contains("goal");
    }

    @Test
    void unscoredMethods_defaultToZero() {
        var json = "[{\"method\": 1, \"score\": 0.9}]";
        var method1 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"))), null);
        var method2 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("b"))), null);

        var heuristic = new LlmDecompositionHeuristic<String>(providerReturning(json));
        var methods = List.of(method1, method2);
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx("state"))
                .await().indefinitely();

        assertThat(scored).hasSize(2);
        assertThat(scored.get(0).score()).isEqualTo(0.9);
        assertThat(scored.get(1).score()).isEqualTo(0.0);
    }
}
