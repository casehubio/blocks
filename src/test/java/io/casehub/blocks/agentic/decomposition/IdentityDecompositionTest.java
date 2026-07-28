package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityDecompositionTest {

    @Test
    void returnsPrimitiveAsSingletonPlan() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(null));
        var primitive = new PrimitiveTask<String>("id1", java.time.Instant.now(), null, agent, null, null);
        var decomp = new IdentityDecomposition<String>();
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

        DagPlan<TaskNode.LeafTask<String>> result = decomp.decompose(primitive, ctx).await().indefinitely();
        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().values().iterator().next().task()).isSameAs(primitive);
    }

    @Test
    void compoundTask_throws() {
        var decomp = new IdentityDecomposition<String>();
        var compound = new TaskNode.CompoundTask<String>("test", List.of());
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

        assertThatThrownBy(() -> decomp.decompose(compound, ctx).await().indefinitely())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("cannot decompose compound tasks");
    }
}
