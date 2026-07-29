package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;

public class StaticDecomposition<T> implements DecompositionStrategy<T> {

    @Override
    public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> compound,
                                                        DecompositionContext<T> context) {
        if (compound instanceof TaskNode.CompoundTask<T> ct) {
            for (var method : ct.methods()) {
                if (method.guard().test(context.state())) {
                    return method.strategy().decompose(compound, context);
                }
            }
            return Uni.createFrom().failure(
                    new NoMethodMatchedException(ct.name(), ct.methods().size()));
        }
        return Uni.createFrom().item(DagPlan.singleton((TaskNode.LeafTask<T>) compound));
    }
}
