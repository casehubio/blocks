package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HybridDecompositionTest {

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static RoutingCandidate candidate(String name, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(name).name(name).slot("default").tenancyId("test")
                .briefing(briefing)
                .capabilities(List.of(AgentCapability.builder().name("work").build()))
                .build();
        return new RoutingCandidate(dummyAgent(), descriptor);
    }

    private static AgentProvider providerReturning(String text) {
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(text)));
        return provider;
    }

    private static DecompositionStrategy<String> staticThatMatches(TaskNode.LeafTask<String> task) {
        return (compound, ctx) -> Uni.createFrom().item(DagPlan.singleton(task));
    }

    private static DecompositionStrategy<String> staticThatFails() {
        return (compound, ctx) -> Uni.createFrom().failure(new NoMethodMatchedException("test-task"));
    }

    private static DecompositionStrategy<String> fallbackReturning(TaskNode.LeafTask<String> task) {
        return (compound, ctx) -> Uni.createFrom().item(DagPlan.singleton(task));
    }

    @Nested
    class StaticSucceeds {
        @Test
        void staticSucceeds_llmNotInvoked() {
            var staticTask = new PrimitiveTask<String>("s1", Instant.now(), "static-task", dummyAgent(), null, null);
            var fallbackTask = new PrimitiveTask<String>("f1", Instant.now(), "fallback-task", dummyAgent(), null, null);

            var hybrid = new HybridDecomposition<>(staticThatMatches(staticTask), fallbackReturning(fallbackTask));
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
            assertThat(result.topologicalSort().get(0).task()).isSameAs(staticTask);
        }

        @Test
        void multipleGuards_firstMatchWins() {
            var agent = dummyAgent();
            var prim = new PrimitiveTask<String>("p1", Instant.now(), null, agent, null, null);
            @SuppressWarnings("unchecked")
            var fallback = (DecompositionStrategy<String>) mock(DecompositionStrategy.class);

            var compound = new TaskNode.CompoundTask<String>("root", List.of(
                    new DecompositionMethod<>(s -> false, (c, x) -> Uni.createFrom().failure(new AssertionError("should not be called"))),
                    new DecompositionMethod<>(s -> true, (c, x) -> Uni.createFrom().item(DagPlan.singleton(prim)))));

            var hybrid = new HybridDecomposition<>(new StaticDecomposition<>(), fallback);
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.topologicalSort().get(0).task()).isSameAs(prim);
            verifyNoInteractions(fallback);
        }
    }

    @Nested
    class Fallback {
        @Test
        void staticFails_llmProducesPlan() {
            var fallbackTask = new PrimitiveTask<String>("f1", Instant.now(), "llm-planned", dummyAgent(), null, null);
            var hybrid = new HybridDecomposition<>(staticThatFails(), fallbackReturning(fallbackTask));
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
            assertThat(result.topologicalSort().get(0).task()).isSameAs(fallbackTask);
        }

        @Test
        void staticFails_fallbackReceivesStaticFailureHint() {
            var captured = new java.util.concurrent.atomic.AtomicReference<DecompositionContext<String>>();
            DecompositionStrategy<String> capturingFallback = (compound, ctx) -> {
                captured.set(ctx);
                var task = new PrimitiveTask<String>("f1", Instant.now(), "planned", dummyAgent(), null, null);
                return Uni.createFrom().item(DagPlan.singleton(task));
            };

            DecompositionStrategy<String> staticWith3Methods = (compound, ctx) ->
                                                                       Uni.createFrom().failure(new NoMethodMatchedException("incident-response", 3));

            var hybrid   = new HybridDecomposition<>(staticWith3Methods, capturingFallback);
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx      = new AgenticDecompositionContext<>("state", List.of(), 0);

            hybrid.decompose(compound, ctx).await().indefinitely();

            assertThat(captured.get()).isInstanceOf(AgenticDecompositionContext.class);
            var enriched = (AgenticDecompositionContext<String>) captured.get();
            assertThat(enriched.staticFailureHint()).contains("3 static method(s) evaluated");
            assertThat(enriched.staticFailureHint()).contains("incident-response");
        }


        @Test
        void staticFails_llmAlsoFails_errorPropagates() {
            DecompositionStrategy<String> failingFallback = (compound, ctx) ->
                    Uni.createFrom().failure(new IllegalStateException("LLM also failed"));

            var hybrid = new HybridDecomposition<>(staticThatFails(), failingFallback);
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            assertThatThrownBy(() -> hybrid.decompose(compound, ctx).await().indefinitely())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LLM also failed");
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void leafTask_passedThrough() {
            var leaf = new PrimitiveTask<String>("l1", Instant.now(), "leaf", dummyAgent(), null, null);
            var static_ = new StaticDecomposition<String>();
            @SuppressWarnings("unchecked")
            var fallback = (DecompositionStrategy<String>) mock(DecompositionStrategy.class);
            var hybrid = new HybridDecomposition<>(static_, fallback);
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(leaf, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
            assertThat(result.topologicalSort().get(0).task()).isSameAs(leaf);
            verifyNoInteractions(fallback);
        }

        @Test
        void nonGuardException_propagatesWithoutFallback() {
            DecompositionStrategy<String> throwing = (compound, ctx) ->
                    Uni.createFrom().failure(new NullPointerException("bug"));
            @SuppressWarnings("unchecked")
            var fallback = (DecompositionStrategy<String>) mock(DecompositionStrategy.class);

            var hybrid = new HybridDecomposition<>(throwing, fallback);
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            assertThatThrownBy(() -> hybrid.decompose(compound, ctx).await().indefinitely())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("bug");
            verifyNoInteractions(fallback);
        }

        @Test
        void fallbackWithEmptyAgents_stillInvokesFallback() {
            var fallbackTask = new PrimitiveTask<String>("f1", Instant.now(), "planned", dummyAgent(), null, null);
            var hybrid = new HybridDecomposition<>(staticThatFails(), fallbackReturning(fallbackTask));
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
        }
    }

    @Nested
    class Constructors {
        @Test
        void convenienceConstructor_createsWorkingInstance() {
            var json = "[{\"agent\": \"analyst\", \"task\": \"analyse\"}]";
            var provider = providerReturning(json);
            var hybrid = new HybridDecomposition<String>(provider);

            var agents = List.of(candidate("analyst", "data analysis"));
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", agents, 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
        }

        @Test
        void stateRendererConstructor_passesRendererToLlm() {
            var json = "[{\"agent\": \"analyst\", \"task\": \"analyse\"}]";
            var provider = providerReturning(json);
            var hybrid = new HybridDecomposition<String>(provider, s -> "RENDERED:" + s);

            var agents = List.of(candidate("analyst", "data analysis"));
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", agents, 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
        }

        @Test
        void fullControlConstructor_usesBothStrategies() {
            var staticTask = new PrimitiveTask<String>("s1", Instant.now(), "static", dummyAgent(), null, null);
            var fallbackTask = new PrimitiveTask<String>("f1", Instant.now(), "fallback", dummyAgent(), null, null);

            var primary = staticThatMatches(staticTask);
            var fallback = fallbackReturning(fallbackTask);
            var hybrid = new HybridDecomposition<>(primary, fallback);

            var compound = new TaskNode.CompoundTask<String>("goal", List.of());
            var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.topologicalSort().get(0).task()).isSameAs(staticTask);
        }
    }
}
