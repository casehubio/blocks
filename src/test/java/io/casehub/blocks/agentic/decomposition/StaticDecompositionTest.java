package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
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
}
