package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;

public class IdentityDecomposition<T> implements DecompositionStrategy<T> {

    @Override
    public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> node,
                                                   DecompositionContext<T> context) {
        return switch (node) {
            case TaskNode.LeafTask<T> leaf -> DagPlan.singleton(leaf);
            case TaskNode.CompoundTask<T> compound -> throw new UnsupportedOperationException(
                    "IdentityDecomposition cannot decompose compound tasks — "
                    + "it is a placeholder for non-HTN builders");
        };
    }
}
