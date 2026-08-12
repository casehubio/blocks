package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.casehub.blocks.agentic.decomposition.Tasks.compound;
import static io.casehub.blocks.agentic.decomposition.Tasks.decompose;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeuristicDecompositionTest {

    private static AgentRef agent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static AgentRef agent(String name) {
        return AgentRef.external(name, s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static AgenticDecompositionContext<String> ctx(String state) {
        return new AgenticDecompositionContext<>(state, List.of(), 0);
    }

    @Test
    void singleEligibleMethod_expandsWithoutInvokingHeuristic() {
        var leaf = new PrimitiveTask<String>("l1", Instant.now(), null, agent(), null, null);
        var heuristicCalled = new AtomicBoolean(false);
        DecompositionHeuristic<String> heuristic = (task, methods, context) -> {
            heuristicCalled.set(true);
            return methods.stream()
                    .map(m -> new ScoredMethod<>(m, 1.0)).toList();
        };

        var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", List.of(
                new DecompositionMethod<String>(s -> true, (c, x) -> DagPlan.singleton(leaf), null)));

        var decomp = new HeuristicDecomposition<>(heuristic);
        var result = decomp.decompose(compound, ctx("state"));

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.topologicalSort().get(0).task()).isSameAs(leaf);
        assertThat(heuristicCalled.get()).isFalse();
    }

    @Test
    void multipleEligibleMethods_expandsBestScored() {
        var lowLeaf = new PrimitiveTask<String>("low", Instant.now(), "low-cost", agent("low"), null, null);
        var highLeaf = new PrimitiveTask<String>("high", Instant.now(), "high-cost", agent("high"), null, null);

        var method1 = new DecompositionMethod<String>(s -> true, (c, x) -> DagPlan.singleton(lowLeaf), null);
        var method2 = new DecompositionMethod<String>(s -> true, (c, x) -> DagPlan.singleton(highLeaf), null);

        DecompositionHeuristic<String> heuristic = (task, methods, context) ->
                List.of(
                        new ScoredMethod<>(methods.get(0), 0.3),
                        new ScoredMethod<>(methods.get(1), 0.9));

        var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", List.of(method1, method2));
        var decomp = new HeuristicDecomposition<>(heuristic);
        var result = decomp.decompose(compound, ctx("state"));

        assertThat(result.topologicalSort().get(0).task().executor().name()).isEqualTo("high");
    }

    @Test
    void backtracking_bestMethodFails_triesNext() {
        var fallbackLeaf = new PrimitiveTask<String>("fb", Instant.now(), "fallback", agent("fallback"), null, null);

        var failingMethod = new DecompositionMethod<String>(s -> true, (c, x) -> { throw new NoMethodMatchedException("inner"); }, null);
        var workingMethod = new DecompositionMethod<String>(s -> true, (c, x) -> DagPlan.singleton(fallbackLeaf), null);

        DecompositionHeuristic<String> heuristic = (task, methods, context) ->
                List.of(
                        new ScoredMethod<>(methods.get(0), 0.9),
                        new ScoredMethod<>(methods.get(1), 0.1));

        var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", List.of(failingMethod, workingMethod));
        var decomp = new HeuristicDecomposition<>(heuristic);
        var result = decomp.decompose(compound, ctx("state"));

        assertThat(result.topologicalSort().get(0).task().executor().name()).isEqualTo("fallback");
    }

    @Test
    void allMethodsFail_throwsNoMethodMatchedException() {
        var failingMethod1 = new DecompositionMethod<String>(s -> true, (c, x) -> { throw new NoMethodMatchedException("m1"); }, null);
        var failingMethod2 = new DecompositionMethod<String>(s -> true, (c, x) -> { throw new NoMethodMatchedException("m2"); }, null);

        DecompositionHeuristic<String> heuristic = (task, methods, context) ->
                List.of(
                        new ScoredMethod<>(methods.get(0), 0.5),
                        new ScoredMethod<>(methods.get(1), 0.5));

        var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", List.of(failingMethod1, failingMethod2));
        var decomp = new HeuristicDecomposition<>(heuristic);

        assertThatThrownBy(() -> decomp.decompose(compound, ctx("state")))
                .isInstanceOf(NoMethodMatchedException.class);
    }

    @Test
    void noEligibleMethods_throwsNoMethodMatchedException() {
        var method = new DecompositionMethod<String>(s -> false, (c, x) -> DagPlan.singleton(
                        new PrimitiveTask<>("x", Instant.now(), null, agent(), null, null)), null);

        DecompositionHeuristic<String> heuristic = (task, methods, context) ->
                List.of();

        var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", List.of(method));
        var decomp = new HeuristicDecomposition<>(heuristic);

        assertThatThrownBy(() -> decomp.decompose(compound, ctx("state")))
                .isInstanceOf(NoMethodMatchedException.class);
    }

    @Test
    void recursivePropagation_heuristicAppliesAtNestedLevels() {
        var cheapLeaf = new PrimitiveTask<String>("cheap", Instant.now(), "cheap", agent("cheap"), null, null);
        var expensiveLeaf = new PrimitiveTask<String>("exp", Instant.now(), "expensive", agent("expensive"), null, null);
        var topLeaf = new PrimitiveTask<String>("top", Instant.now(), "top", agent("top"), null, null);

        var nestedCompound = new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "nested", List.of(
                new DecompositionMethod<String>(s -> true,
                        (c, x) -> DagPlan.singleton(expensiveLeaf), null),
                new DecompositionMethod<String>(s -> true,
                        (c, x) -> DagPlan.singleton(cheapLeaf), null)));

        TaskNode.CompoundTask<String> topCompound = compound("top-level", topLeaf, nestedCompound);

        DecompositionHeuristic<String> heuristic = (task, methods, context) ->
                java.util.stream.IntStream.range(0, methods.size())
                        .mapToObj(i -> new ScoredMethod<>(methods.get(i), (double) i))
                        .toList();

        var decomp = new HeuristicDecomposition<>(heuristic);
        var result = decomp.decompose(topCompound, ctx("state"));

        var sorted = result.topologicalSort();
        assertThat(sorted).hasSize(2);
        assertThat(sorted.get(0).task().executor().name()).isEqualTo("top");
        assertThat(sorted.get(1).task().executor().name()).isEqualTo("cheap");
    }

    @Test
    void leafTask_returnsSingletonPlan() {
        var leaf = new PrimitiveTask<String>("l", Instant.now(), null, agent(), null, null);
        DecompositionHeuristic<String> heuristic = (task, methods, context) -> {
            throw new AssertionError("should not be called");
        };

        var decomp = new HeuristicDecomposition<>(heuristic);
        var result = decomp.decompose(leaf, ctx("state"));

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.topologicalSort().get(0).task()).isSameAs(leaf);
    }
}
