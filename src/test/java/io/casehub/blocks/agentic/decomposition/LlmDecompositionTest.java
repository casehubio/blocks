package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.engine.plan.TaskNode;
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

    private static AgentProvider sequentialProvider(String... responses) {
        var provider = mock(AgentProvider.class);
        var index    = new java.util.concurrent.atomic.AtomicInteger(0);
        when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
            int i = index.getAndIncrement();
            if (i >= responses.length) {
                return Multi.createFrom().failure(new RuntimeException("No more responses"));
            }
            return Multi.createFrom().item(new AgentEvent.TextDelta(responses[i]));
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
            var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "investigate", List.of());

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
            var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "goal", List.of());

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

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
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

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
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

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
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

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                    .await().indefinitely())
                .isInstanceOf(Exception.class);
        }

        @Test
        void throwsWhenAgentProviderFails() {
            var decomp = new LlmDecomposition<String>(failingProvider());
            var agents = List.of(candidate("a", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                    .await().indefinitely())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void throwsOnEmptyLlmPlan() {
            var decomp = new LlmDecomposition<String>(providerReturning("[]"));
            var agents = List.of(candidate("a", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
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

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
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

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
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

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "investigate-fraud", List.of()), ctx)
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

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).contains("analyst");
            assertThat(promptCapture.get()).contains("expert in data analysis");
        }

        @Test
        void includesStaticFailureHintWhenPresent() {
            var promptCapture = new AtomicReference<String>();
            var provider      = capturingProvider(promptCapture, "analyst");
            var decomp        = new LlmDecomposition<String>(provider);
            var agents        = List.of(candidate("analyst", "a"));
            var ctx = new AgenticDecompositionContext<>("s", agents, 0,
                                                        "3 static method(s) evaluated, none matched for 'respond-to-incident'");

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                  .await().indefinitely();

            assertThat(promptCapture.get()).contains("3 static method(s) evaluated");
            assertThat(promptCapture.get()).contains("respond-to-incident");
        }

    }

    @Nested
    class RecursiveDecomposition {
        @Test
        void mixedResponseProducesCorrectMergedDag() {
            var topLevel = """
                           [{"agent": "analyst", "task": "review data", "rationale": "expert"},
                            {"subtask": "prepare-report", "description": "compile findings"}]
                           """;
            var subtaskPlan = """
                              [{"agent": "reporter", "task": "draft report", "rationale": "writer"},
                               {"agent": "reviewer", "task": "review report", "rationale": "quality"}]
                              """;
            var provider = sequentialProvider(topLevel, subtaskPlan);
            var decomp   = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents   = List.of(candidate("analyst", "a"), candidate("reporter", "r"), candidate("reviewer", "v"));
            var ctx      = new AgenticDecompositionContext<>("state", agents, 0);
            var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "investigate", List.of());

            var result = decomp.decompose(compound, ctx).await().indefinitely();

            var sorted = result.topologicalSort();
            assertThat(sorted).hasSize(3);
            assertThat(((PlannedTask<String>) sorted.get(0).task()).description()).isEqualTo("review data");
            assertThat(((PlannedTask<String>) sorted.get(1).task()).description()).isEqualTo("draft report");
            assertThat(((PlannedTask<String>) sorted.get(2).task()).description()).isEqualTo("review report");
        }

        @Test
        void allSubtasksRecursivelyDecomposed() {
            var topLevel = """
                           [{"subtask": "phase-1", "description": "first phase"},
                            {"subtask": "phase-2", "description": "second phase"}]
                           """;
            var phase1Plan = """
                             [{"agent": "analyst", "task": "step-1a"}]
                             """;
            var phase2Plan = """
                             [{"agent": "reporter", "task": "step-2a"},
                              {"agent": "analyst", "task": "step-2b"}]
                             """;
            var provider = sequentialProvider(topLevel, phase1Plan, phase2Plan);
            var decomp   = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents   = List.of(candidate("analyst", "a"), candidate("reporter", "r"));
            var ctx      = new AgenticDecompositionContext<>("state", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "goal", List.of()), ctx)
                               .await().indefinitely();

            var sorted = result.topologicalSort();
            assertThat(sorted).hasSize(3);
            assertThat(((PlannedTask<String>) sorted.get(0).task()).description()).isEqualTo("step-1a");
            assertThat(((PlannedTask<String>) sorted.get(1).task()).description()).isEqualTo("step-2a");
            assertThat(((PlannedTask<String>) sorted.get(2).task()).description()).isEqualTo("step-2b");
        }

        @Test
        void threeLevelRecursionProducesCorrectDag() {
            var level0 = """
                         [{"subtask": "sub-a", "description": "part a"}]
                         """;
            var level1 = """
                         [{"subtask": "sub-a1", "description": "sub part"}]
                         """;
            var level2 = """
                         [{"agent": "analyst", "task": "leaf-task"}]
                         """;
            var provider = sequentialProvider(level0, level1, level2);
            var decomp   = new LlmDecomposition<String>(provider, Object::toString, 3);
            var agents   = List.of(candidate("analyst", "a"));
            var ctx      = new AgenticDecompositionContext<>("state", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "root", List.of()), ctx)
                               .await().indefinitely();

            assertThat(result.topologicalSort()).hasSize(1);
            assertThat(((PlannedTask<String>) result.topologicalSort().get(0).task()).description())
                    .isEqualTo("leaf-task");
        }

        @Test
        void taskOrderPreservedAcrossRecursiveBoundaries() {
            var topLevel = """
                           [{"agent": "analyst", "task": "first"},
                            {"subtask": "middle", "description": "middle phase"},
                            {"agent": "reporter", "task": "last"}]
                           """;
            var middlePlan = """
                             [{"agent": "analyst", "task": "middle-step"}]
                             """;
            var provider = sequentialProvider(topLevel, middlePlan);
            var decomp   = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents   = List.of(candidate("analyst", "a"), candidate("reporter", "r"));
            var ctx      = new AgenticDecompositionContext<>("state", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "goal", List.of()), ctx)
                               .await().indefinitely();

            var sorted = result.topologicalSort();
            assertThat(sorted).hasSize(3);
            assertThat(((PlannedTask<String>) sorted.get(0).task()).description()).isEqualTo("first");
            assertThat(((PlannedTask<String>) sorted.get(1).task()).description()).isEqualTo("middle-step");
            assertThat(((PlannedTask<String>) sorted.get(2).task()).description()).isEqualTo("last");
        }
    }

    @Nested
    class DepthEnforcement {
        @Test
        void maxDepthOneSkipsSubtaskEntries() {
            var json = """
                       [{"agent": "analyst", "task": "keep me"},
                        {"subtask": "skip-me", "description": "should be skipped"},
                        {"agent": "reporter", "task": "also keep"}]
                       """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"), candidate("reporter", "r"));
            var ctx    = new AgenticDecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                               .await().indefinitely();

            assertThat(result.topologicalSort()).hasSize(2);
            assertThat(((PlannedTask<String>) result.topologicalSort().get(0).task()).description())
                    .isEqualTo("keep me");
            assertThat(((PlannedTask<String>) result.topologicalSort().get(1).task()).description())
                    .isEqualTo("also keep");
        }

        @Test
        void maxDepthTwoRecursiveCallUsesFlatPrompt() {
            var promptCapture = new java.util.concurrent.atomic.AtomicReference<String>();
            var provider      = mock(AgentProvider.class);
            var callCount     = new java.util.concurrent.atomic.AtomicInteger(0);
            when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
                int call   = callCount.getAndIncrement();
                var config = invocation.getArgument(0, AgentSessionConfig.class);
                if (call == 0) {
                    return Multi.createFrom().item(new AgentEvent.TextDelta(
                            "[{\"subtask\": \"sub\", \"description\": \"sub desc\"}]"));
                } else {
                    promptCapture.set(config.systemPrompt());
                    return Multi.createFrom().item(new AgentEvent.TextDelta(
                            "[{\"agent\": \"analyst\", \"task\": \"leaf\"}]"));
                }
            });
            var decomp = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents = List.of(candidate("analyst", "a"));
            var ctx    = new AgenticDecompositionContext<>("s", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                  .await().indefinitely();

            assertThat(promptCapture.get()).doesNotContain("subtask");
            assertThat(promptCapture.get()).contains("agent");
        }
    }

    @Nested
    class ConstructorValidation {
        @Test
        void maxDepthLessThanOneThrows() {
            assertThatThrownBy(() -> new LlmDecomposition<String>(providerReturning("[]"), Object::toString, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void maxDepthOneIsDefault() {
            var json = """
                       [{"agent": "analyst", "task": "work"}]
                       """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx    = new AgenticDecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                               .await().indefinitely();

            assertThat(result.nodes()).hasSize(1);
        }
    }

    @Nested
    class RecursivePromptConstruction {
        @Test
        void recursivePromptIncludesParentGoalAndSiblings() {
            var promptCapture = new java.util.concurrent.atomic.AtomicReference<String>();
            var provider      = mock(AgentProvider.class);
            var callCount     = new java.util.concurrent.atomic.AtomicInteger(0);
            when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
                int call   = callCount.getAndIncrement();
                var config = invocation.getArgument(0, AgentSessionConfig.class);
                if (call == 0) {
                    return Multi.createFrom().item(new AgentEvent.TextDelta(
                            "[{\"agent\": \"analyst\", \"task\": \"step-1\"}, {\"subtask\": \"sub-task\", \"description\": \"detailed work\"}]"));
                } else {
                    promptCapture.set(config.userPrompt());
                    return Multi.createFrom().item(new AgentEvent.TextDelta(
                            "[{\"agent\": \"analyst\", \"task\": \"leaf\"}]"));
                }
            });
            var decomp = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents = List.of(candidate("analyst", "a"));
            var ctx    = new AgenticDecompositionContext<>("s", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "parent-goal", List.of()), ctx)
                  .await().indefinitely();

            assertThat(promptCapture.get()).contains("parent-goal");
            assertThat(promptCapture.get()).contains("sub-task");
            assertThat(promptCapture.get()).contains("detailed work");
        }

        @Test
        void recursivePromptIncludesSubtaskFormat() {
            var promptCapture = new java.util.concurrent.atomic.AtomicReference<String>();
            var provider      = mock(AgentProvider.class);
            when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
                var config = invocation.getArgument(0, AgentSessionConfig.class);
                promptCapture.set(config.systemPrompt());
                return Multi.createFrom().item(new AgentEvent.TextDelta(
                        "[{\"agent\": \"analyst\", \"task\": \"work\"}]"));
            });
            var decomp = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents = List.of(candidate("analyst", "a"));
            var ctx    = new AgenticDecompositionContext<>("s", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                  .await().indefinitely();

            assertThat(promptCapture.get()).contains("subtask");
        }
    }

    @Nested
    class RecursiveErrorHandling {
        @Test
        void emptyRecursiveDecompositionThrowsWithContext() {
            var topLevel = """
                           [{"subtask": "failing-sub", "description": "will fail"}]
                           """;
            var emptyResult = "[]";
            var provider    = sequentialProvider(topLevel, emptyResult);
            var decomp      = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents      = List.of(candidate("analyst", "a"));
            var ctx         = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                                           .await().indefinitely())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("empty plan");
        }

        @Test
        void llmFailureDuringRecursionPropagates() {
            var topLevel = """
                           [{"subtask": "failing-sub", "description": "will fail"}]
                           """;
            var provider  = mock(AgentProvider.class);
            var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
            when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
                int call = callCount.getAndIncrement();
                if (call == 0) {
                    return Multi.createFrom().item(new AgentEvent.TextDelta(topLevel));
                }
                return Multi.createFrom().failure(new RuntimeException("LLM crashed"));
            });
            var decomp = new LlmDecomposition<String>(provider, Object::toString, 2);
            var agents = List.of(candidate("analyst", "a"));
            var ctx    = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                                           .await().indefinitely())
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void allEntriesSkippedAtMaxDepthThrows() {
            var json = """
                       [{"subtask": "sub1", "description": "d1"},
                        {"subtask": "sub2", "description": "d2"}]
                       """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx    = new AgenticDecompositionContext<>("s", agents, 0);

            assertThatThrownBy(() -> decomp.decompose(new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "g", List.of()), ctx)
                                           .await().indefinitely())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("empty plan");
        }
    }


}
