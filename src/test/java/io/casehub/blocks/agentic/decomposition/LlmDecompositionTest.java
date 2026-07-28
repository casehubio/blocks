package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.TaskNode;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDecompositionTest {

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static RoutingCandidate candidate(String name, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(name).name(name).slot("default").tenancyId("test")
                .briefing(briefing)
                .capabilities(List.of(AgentCapability.builder().name("analysis").build()))
                .build();
        return new RoutingCandidate(dummyAgent(), descriptor);
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

    private static AgentProvider capturingProvider(AtomicReference<String> capture, String agentName) {
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
            var config = invocation.getArgument(0, AgentSessionConfig.class);
            capture.set(config.userPrompt());
            return Multi.createFrom().item(new AgentEvent.TextDelta(
                "[{\"agent\": \"" + agentName + "\", \"task\": \"captured\"}]"));
        });
        return provider;
    }

    @Nested
    class HappyPath {
        @Test
        void decomposesGoalIntoPlannedTasks() {
            var json = """
                    [{"agent": "analyst", "task": "review the data", "rationale": "domain expert"},
                     {"agent": "reporter", "task": "write the report", "rationale": "writing skills"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "data analysis"), candidate("reporter", "reporting"));
            var ctx = new AgenticDecompositionContext<>("initial state", agents, 0);
            var compound = new TaskNode.CompoundTask<String>("investigate", List.of());

            var result = decomp.decompose(compound, ctx).await().indefinitely();

            assertThat(result.nodes()).hasSize(2);
            assertThat(result.topologicalSort().get(0).task()).isInstanceOf(PlannedTask.class);
            var t0 = (PlannedTask<String>) result.topologicalSort().get(0).task();
            assertThat(t0.description()).isEqualTo("review the data");
            assertThat(t0.rationale()).isEqualTo("domain expert");
            var t1 = (PlannedTask<String>) result.topologicalSort().get(1).task();
            assertThat(t1.description()).isEqualTo("write the report");
        }

        @Test
        void preservesTaskOrdering() {
            var json = """
                    [{"agent": "reporter", "task": "step-1"},
                     {"agent": "analyst", "task": "step-2"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"), candidate("reporter", "r"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());

            var result = decomp.decompose(compound, ctx).await().indefinitely();

            assertThat(result.nodes()).hasSize(2);
            assertThat(((PlannedTask<String>) result.topologicalSort().get(0).task()).description()).isEqualTo("step-1");
            assertThat(((PlannedTask<String>) result.topologicalSort().get(1).task()).description()).isEqualTo("step-2");
        }

        @Test
        void parsesCodeFenceWrappedJson() {
            var json = """
                    ```json
                    [{"agent": "analyst", "task": "do work"}]
                    ```
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result.nodes()).hasSize(1);
        }
    }

    @Nested
    class AgentMatching {
        @Test
        void mapsAgentNamesByDescriptorName() {
            var json = """
                    [{"agent": "analyst", "task": "work"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result.nodes()).hasSize(1);
            assertThat(((PlannedTask<String>) result.topologicalSort().get(0).task()).agent())
                    .isSameAs(agents.get(0).ref());
        }

        @Test
        void skipsUnknownAgentNames() {
            var json = """
                    [{"agent": "unknown", "task": "skip me"},
                     {"agent": "analyst", "task": "keep me"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result.nodes()).hasSize(1);
            assertThat(((PlannedTask<String>) result.topologicalSort().get(0).task()).description()).isEqualTo("keep me");
        }
    }

    @Nested
    class ErrorHandling {
        @Test
        void throwsOnUnparseableResponse() {
            var decomp = new LlmDecomposition<String>(providerReturning("not json at all"));
            var agents = List.of(candidate("a", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely())
                .isInstanceOf(Exception.class);
        }

        @Test
        void throwsWhenAgentProviderFails() {
            var decomp = new LlmDecomposition<String>(failingProvider());
            var agents = List.of(candidate("a", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void throwsOnEmptyLlmPlan() {
            var decomp = new LlmDecomposition<String>(providerReturning("[]"));
            var agents = List.of(candidate("a", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely())
                .hasMessageContaining("empty plan");
        }

        @Test
        void returnsInputUnchangedForNonCompoundTask() {
            var decomp = new LlmDecomposition<String>(failingProvider());
            var leaf = new PlannedTask<String>("id1", java.time.Instant.now(), "task", dummyAgent(), null);
            var ctx = new AgenticDecompositionContext<String>("s", List.of(), 0);

            var result = decomp.decompose(leaf, ctx).await().indefinitely();

            assertThat(result.nodes()).hasSize(1);
            assertThat(result.topologicalSort().get(0).task()).isSameAs(leaf);
        }
    }

    @Nested
    class PromptConstruction {
        @Test
        void includesStateInPromptWhenPresent() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture, "analyst");
            var decomp = new LlmDecomposition<String>(provider, s -> "STATE:" + s);
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new AgenticDecompositionContext<>("my-state", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).contains("STATE:my-state");
        }

        @Test
        void omitsStateFromPromptWhenNull() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture, "analyst");
            var decomp = new LlmDecomposition<String>(provider);
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new AgenticDecompositionContext<String>(null, agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).doesNotContain("Current state");
        }

        @Test
        void passesCompoundTaskNameAsGoal() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture, "analyst");
            var decomp = new LlmDecomposition<String>(provider);
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("investigate-fraud", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).contains("investigate-fraud");
        }

        @Test
        void includesAgentCardsInPrompt() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture, "analyst");
            var decomp = new LlmDecomposition<String>(provider);
            var agents = List.of(candidate("analyst", "expert in data analysis"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).contains("analyst");
            assertThat(promptCapture.get()).contains("expert in data analysis");
        }
    }
}
