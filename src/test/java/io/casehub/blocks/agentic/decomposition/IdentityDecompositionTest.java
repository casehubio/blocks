package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityDecompositionTest {

    @Test
    void returnsPrimitiveAsIs() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(null));
        var primitive = new TaskNode.PrimitiveTask<String>(null, agent, null, null);
        var decomp = new IdentityDecomposition<String>();
        var ctx = new DecompositionContext<>("state", List.of(), 0);

        var result = decomp.decompose(primitive, ctx).await().indefinitely();
        assertThat(result).containsExactly(primitive);
    }
}
