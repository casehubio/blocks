package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class StaticDecompositionTest {

    @Test
    void selectsFirstMatchingMethod() {
        var agent1 = AgentRef.external(s -> CompletableFuture.completedFuture(null));
        var agent2 = AgentRef.external(s -> CompletableFuture.completedFuture(null));
        var prim1 = new TaskNode.PrimitiveTask<String>(null, agent1, null, null);
        var prim2 = new TaskNode.PrimitiveTask<String>(null, agent2, null, null);

        DecompositionStrategy<String> strategy1 = (compound, ctx) ->
                io.smallrye.mutiny.Uni.createFrom().item(List.of(prim1));
        DecompositionStrategy<String> strategy2 = (compound, ctx) ->
                io.smallrye.mutiny.Uni.createFrom().item(List.of(prim2));

        var method1 = new DecompositionMethod<String>(s -> s.equals("match"), strategy1);
        var method2 = new DecompositionMethod<String>(s -> true, strategy2);

        var compound = new TaskNode.CompoundTask<>("root", List.of(method1, method2));
        var decomp = new StaticDecomposition<String>();
        var ctx = new DecompositionContext<>("match", List.of(), 0);

        var result = decomp.decompose(compound, ctx).await().indefinitely();
        assertThat(result).containsExactly(prim1);
    }

    @Test
    void returnsEmptyWhenNoMethodMatches() {
        var compound = new TaskNode.CompoundTask<String>("root", List.of(
                new DecompositionMethod<>(s -> false, new IdentityDecomposition<>())));
        var decomp = new StaticDecomposition<String>();
        var ctx = new DecompositionContext<>("state", List.of(), 0);

        var result = decomp.decompose(compound, ctx).await().indefinitely();
        assertThat(result).isEmpty();
    }
}
