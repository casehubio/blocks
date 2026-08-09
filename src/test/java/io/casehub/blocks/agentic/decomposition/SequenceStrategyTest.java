package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceStrategyTest {

    private static AgentRef agent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    @Test
    void usesContextDecomposerForNestedCompoundTasks() {
        var leaf1 = new PrimitiveTask<String>("l1", Instant.now(), "first", agent(), null, null);
        var leaf2 = new PrimitiveTask<String>("l2", Instant.now(), "second", agent(), null, null);

        TaskNode.CompoundTask<String> nestedCompound = new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "nested", List.of(
                new DecompositionMethod<String>(s -> true, (c, x) -> Uni.createFrom().item(DagPlan.singleton(leaf2)), null)));

        var decomposerCalled = new AtomicBoolean(false);
        DecompositionStrategy<String> customDecomposer = (node, x) -> {
            if (node instanceof TaskNode.CompoundTask<String> ct) {
                decomposerCalled.set(true);
                return ct.methods().get(0).strategy().decompose(ct, x);
            }
            return Uni.createFrom().item(DagPlan.singleton((TaskNode.LeafTask<String>) node));
        };

        var seq = new SequenceStrategy<>(List.<TaskNode<String>>of(leaf1, nestedCompound));
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0, null, null, null, null, customDecomposer);

        var plan = seq.decompose(leaf1, ctx).await().indefinitely();
        assertThat(decomposerCalled.get()).isTrue();
        assertThat(plan.nodes()).hasSize(2);
    }

    @Test
    void fallsBackToStaticDecompositionWhenNoDecomposer() {
        var leaf1 = new PrimitiveTask<String>("l1", Instant.now(), "first", agent(), null, null);
        var leaf2 = new PrimitiveTask<String>("l2", Instant.now(), "second", agent(), null, null);

        TaskNode.CompoundTask<String> nestedCompound = new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "nested", List.of(
                new DecompositionMethod<String>(s -> true, (c, x) -> Uni.createFrom().item(DagPlan.singleton(leaf2)), null)));

        var seq = new SequenceStrategy<>(List.<TaskNode<String>>of(leaf1, nestedCompound));
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

        var plan = seq.decompose(leaf1, ctx).await().indefinitely();
        assertThat(plan.nodes()).hasSize(2);
    }
}
