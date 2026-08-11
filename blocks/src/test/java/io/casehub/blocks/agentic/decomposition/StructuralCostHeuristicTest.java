package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralCostHeuristicTest {

    private static AgentRef agent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static PrimitiveTask<String> leaf(String id) {
        return new PrimitiveTask<>(id, Instant.now(), id, agent(), null, null);
    }

    private static AgenticDecompositionContext<String> ctx() {
        return new AgenticDecompositionContext<>("state", List.of(), 0);
    }

    @Test
    void prefersMethodWithFewerLeafTasks() {
        var method1 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"), leaf("b"), leaf("c"))), null);
        var method2 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("x"))), null);

        var methods = List.of(method1, method2);
        var heuristic = new StructuralCostHeuristic<String>();
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx())
                .await().indefinitely();

        assertThat(scored).hasSize(2);
        var best = scored.stream().max(Comparator.comparingDouble(ScoredMethod::score)).orElseThrow();
        assertThat(best.method()).isSameAs(method2);
    }

    @Test
    void nestedCompoundTask_usesMinimumCostAcrossMethods() {
        var nestedCompound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "nested", List.of(
                new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("x"), leaf("y"), leaf("z"))), null),
                new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"))), null)));

        var method1 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.<TaskNode<String>>of(leaf("top"), nestedCompound)), null);
        var method2 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"), leaf("b"))), null);

        var methods = List.of(method1, method2);
        var heuristic = new StructuralCostHeuristic<String>();
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx())
                .await().indefinitely();

        assertThat(scored.get(0).score()).isEqualTo(scored.get(1).score());
    }

    @Test
    void opaqueStrategy_usesConfiguredDefaultCost() {
        var method1 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"), leaf("b"))), null);
        var method2 = new DecompositionMethod<String>(s -> true, (c, x) -> Uni.createFrom().item(DagPlan.singleton(leaf("x"))), null);

        var methods = List.of(method1, method2);
        var heuristic = new StructuralCostHeuristic<String>(5.0);
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx())
                .await().indefinitely();

        var seqScore = scored.stream().filter(s -> s.method() == method1).findFirst().orElseThrow().score();
        var opaqueScore = scored.stream().filter(s -> s.method() == method2).findFirst().orElseThrow().score();
        assertThat(seqScore).isGreaterThan(opaqueScore);
    }

    @Test
    void returnsOneScorePerInputMethod() {
        var method1 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("a"))), null);
        var method2 = new DecompositionMethod<String>(s -> true, new SequenceStrategy<>(List.of(leaf("b"))), null);
        var method3 = new DecompositionMethod<String>(s -> true, (c, x) -> Uni.createFrom().item(DagPlan.singleton(leaf("c"))), null);

        var methods = List.of(method1, method2, method3);
        var heuristic = new StructuralCostHeuristic<String>();
        var scored = heuristic.evaluate(
                new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx())
                .await().indefinitely();

        assertThat(scored).hasSize(3);
    }
}
