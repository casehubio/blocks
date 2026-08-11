package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.PlanningConstraints;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticDecompositionTest {

    @Test
    void selectsFirstMatchingMethod() {
        var agent1 = AgentRef.external(s -> CompletableFuture.completedFuture(null));
        var prim1  = new PrimitiveTask<String>("s1", java.time.Instant.now(), null, agent1, null, null);

        DecompositionStrategy<String> strategy1 = (compound, ctx) ->
                                                          io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(prim1));

        var method1 = new DecompositionMethod<String>(s -> s.equals("match"), strategy1, null);
        var method2 = new DecompositionMethod<String>(s -> true, new IdentityDecomposition<>(), null);

        var compound = new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "root", List.of(method1, method2));
        var decomp   = new StaticDecomposition<String>();
        var ctx      = new AgenticDecompositionContext<>("match", List.of(), 0);

        DagPlan<TaskNode.LeafTask<String>> result = decomp.decompose(compound, ctx).await().indefinitely();
        assertThat(result.nodes()).hasSize(1);
        assertThat(result.topologicalSort().get(0).task()).isSameAs(prim1);
    }

    @Test
    void throwsWhenNoMethodMatches() {
        var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", List.of(
                new DecompositionMethod<>(s -> false, new IdentityDecomposition<>(), null)));
        var decomp = new StaticDecomposition<String>();
        var ctx    = new AgenticDecompositionContext<>("state", List.of(), 0);

        assertThatThrownBy(() -> decomp.decompose(compound, ctx).await().indefinitely())
                .isInstanceOf(NoMethodMatchedException.class)
                .hasMessageContaining("No decomposition method guard matched")
                .extracting(e -> ((NoMethodMatchedException) e).taskName())
                .isEqualTo("root");
    }

    @Test
    void constraintsFlowThroughToSubStrategy() {
        var constraints = PlanningConstraints.of(java.time.Duration.ofMinutes(10), 2);
        var captured    = new java.util.concurrent.atomic.AtomicReference<io.casehub.engine.plan.DecompositionContext<String>>();

        DecompositionStrategy<String> capturing = (compound, ctx) -> {
            captured.set(ctx);
            var agent = AgentRef.external(s -> java.util.concurrent.CompletableFuture.completedFuture(null));
            var leaf  = new PrimitiveTask<String>("t", java.time.Instant.now(), null, agent, null, null);
            return io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(leaf));
        };

        var method   = new DecompositionMethod<String>(s -> true, capturing, null);
        var compound = new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "root", List.of(method));
        var decomp   = new StaticDecomposition<String>();
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0,
                                                    null, null, null, null, null, constraints);

        decomp.decompose(compound, ctx).await().indefinitely();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().constraints()).isSameAs(constraints);
    }

    @Test
    void skipsMethodsExceedingCostBudget() {
        var agent     = AgentRef.external(s -> java.util.concurrent.CompletableFuture.completedFuture(null));
        var expensive = new PrimitiveTask<String>("expensive", java.time.Instant.now(), null, agent, null, null);
        var cheap     = new PrimitiveTask<String>("cheap", java.time.Instant.now(), null, agent, null, null);

        DecompositionStrategy<String> expensiveStrategy = (c, ctx) ->
                                                                  io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(expensive));
        DecompositionStrategy<String> cheapStrategy = (c, ctx) ->
                                                              io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(cheap));

        var expensiveMethod = new DecompositionMethod<String>(
                "expensive", s -> true, expensiveStrategy, null,
                java.util.Map.of("tokens", 10000), null);
        var cheapMethod = new DecompositionMethod<String>(
                "cheap", s -> true, cheapStrategy, null,
                java.util.Map.of("tokens", 100), null);

        var compound = new TaskNode.CompoundTask<>(
                java.util.UUID.randomUUID().toString(), "root",
                java.util.List.of(expensiveMethod, cheapMethod));
        var constraints = new PlanningConstraints(null, null, java.util.Map.of(), java.util.Map.of("tokens", 5000));
        var ctx = new AgenticDecompositionContext<>("state", java.util.List.of(), 0,
                                                    null, null, null, null, null, constraints);
        var decomp = new StaticDecomposition<String>();

        DagPlan<TaskNode.LeafTask<String>> result = decomp.decompose(compound, ctx).await().indefinitely();
        assertThat(result.topologicalSort().get(0).task()).isSameAs(cheap);
    }

    @Test
    void methodsWithoutEstimatesAreNotSkipped() {
        var agent = AgentRef.external(s -> java.util.concurrent.CompletableFuture.completedFuture(null));
        var prim  = new PrimitiveTask<String>("t", java.time.Instant.now(), null, agent, null, null);

        DecompositionStrategy<String> strategy = (c, ctx) ->
                                                         io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(prim));

        var method = new DecompositionMethod<String>(s -> true, strategy, null);

        var compound = new TaskNode.CompoundTask<>(
                java.util.UUID.randomUUID().toString(), "root", java.util.List.of(method));
        var constraints = new PlanningConstraints(null, null, java.util.Map.of(), java.util.Map.of("tokens", 100));
        var ctx = new AgenticDecompositionContext<>("state", java.util.List.of(), 0,
                                                    null, null, null, null, null, constraints);
        var decomp = new StaticDecomposition<String>();

        DagPlan<TaskNode.LeafTask<String>> result = decomp.decompose(compound, ctx).await().indefinitely();
        assertThat(result.nodes()).hasSize(1);
    }

    @Test
    void methodsWithinBudgetAreNotSkipped() {
        var agent = AgentRef.external(s -> java.util.concurrent.CompletableFuture.completedFuture(null));
        var prim  = new PrimitiveTask<String>("t", java.time.Instant.now(), null, agent, null, null);

        DecompositionStrategy<String> strategy = (c, ctx) ->
                                                         io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(prim));

        var method = new DecompositionMethod<String>(
                "within-budget", s -> true, strategy, null,
                java.util.Map.of("tokens", 3000), null);

        var compound = new TaskNode.CompoundTask<>(
                java.util.UUID.randomUUID().toString(), "root", java.util.List.of(method));
        var constraints = new PlanningConstraints(null, null, java.util.Map.of(), java.util.Map.of("tokens", 5000));
        var ctx = new AgenticDecompositionContext<>("state", java.util.List.of(), 0,
                                                    null, null, null, null, null, constraints);
        var decomp = new StaticDecomposition<String>();

        DagPlan<TaskNode.LeafTask<String>> result = decomp.decompose(compound, ctx).await().indefinitely();
        assertThat(result.nodes()).hasSize(1);
    }

    @Test
    void skipsMethodsExceedingDurationEstimate() {
        var agent = AgentRef.external(s -> java.util.concurrent.CompletableFuture.completedFuture(null));
        var slow  = new PrimitiveTask<String>("slow", java.time.Instant.now(), null, agent, null, null);
        var fast  = new PrimitiveTask<String>("fast", java.time.Instant.now(), null, agent, null, null);

        DecompositionStrategy<String> slowStrategy = (c, ctx) ->
                                                             io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(slow));
        DecompositionStrategy<String> fastStrategy = (c, ctx) ->
                                                             io.smallrye.mutiny.Uni.createFrom().item(DagPlan.singleton(fast));

        var slowMethod = new DecompositionMethod<String>(
                "slow", s -> true, slowStrategy, null,
                null, java.time.Duration.ofHours(2));
        var fastMethod = new DecompositionMethod<String>(
                "fast", s -> true, fastStrategy, null,
                null, java.time.Duration.ofMinutes(10));

        var compound = new TaskNode.CompoundTask<>(
                java.util.UUID.randomUUID().toString(), "root",
                java.util.List.of(slowMethod, fastMethod));
        var constraints = PlanningConstraints.of(java.time.Duration.ofMinutes(30), null);
        var ctx = new AgenticDecompositionContext<>("state", java.util.List.of(), 0,
                                                    null, null, null, null, null, constraints);
        var decomp = new StaticDecomposition<String>();

        DagPlan<TaskNode.LeafTask<String>> result = decomp.decompose(compound, ctx).await().indefinitely();
        assertThat(result.topologicalSort().get(0).task()).isSameAs(fast);
    }


}
