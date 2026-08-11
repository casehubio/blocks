package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;

final class SequenceStrategy<T> implements DecompositionStrategy<T> {

    private final List<TaskNode<T>> children;

    SequenceStrategy(List<TaskNode<T>> children) {
        this.children = List.copyOf(children);
    }

    List<TaskNode<T>> children() {
        return children;
    }

    @Override
    public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> ignored, DecompositionContext<T> ctx) {
        DecompositionStrategy<T> decomposer =
                (ctx instanceof AgenticDecompositionContext<T> adc && adc.decomposer() != null)
                ? adc.decomposer() : new StaticDecomposition<>();
        
        var plans = new ArrayList<DagPlan<TaskNode.LeafTask<T>>>();
        for (var child : children) {
            plans.add(resolvePlan(child, decomposer, ctx));
        }
        return DagPlan.sequentialMerge(plans);
    }

    private static <T> DagPlan<TaskNode.LeafTask<T>> resolvePlan(
            TaskNode<T> node, DecompositionStrategy<T> decomposer, DecompositionContext<T> ctx) {
        if (node instanceof TaskNode.LeafTask<T> leaf) {
            return DagPlan.singleton(leaf);
        }
        return decomposer.decompose(node, ctx);
    }
}
