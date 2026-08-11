package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.PlanningConstraints;
import io.casehub.engine.plan.TaskNode;

public class StaticDecomposition<T> implements DecompositionStrategy<T> {

    @Override
    public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> compound,
                                                    DecompositionContext<T> context) {
        if (compound instanceof TaskNode.CompoundTask<T> ct) {
            var constraints = context.constraints();
            for (var method : ct.methods()) {
                if (exceedsConstraints(method, constraints)) {
                    continue;
                }
                if (method.guard().test(context.state())) {
                    return method.strategy().decompose(compound, context);
                }
            }
            throw new NoMethodMatchedException(ct.name(), ct.methods().size());
        }
        return DagPlan.singleton((TaskNode.LeafTask<T>) compound);
    }

    private boolean exceedsConstraints(DecompositionMethod<T> method,
                                        PlanningConstraints constraints) {
        if (method.estimatedDuration() != null && constraints.timeBudget() != null
                && method.estimatedDuration().compareTo(constraints.timeBudget()) > 0) {
            return true;
        }
        if (method.estimatedCost() != null && !constraints.costBudgets().isEmpty()) {
            for (var entry : method.estimatedCost().entrySet()) {
                var budget = constraints.costBudgets().get(entry.getKey());
                if (budget != null && entry.getValue() > budget) {
                    return true;
                }
            }
        }
        return false;
    }
}
