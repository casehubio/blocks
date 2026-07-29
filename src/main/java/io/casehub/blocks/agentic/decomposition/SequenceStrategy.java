package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;

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
    public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> ignored, DecompositionContext<T> ctx) {
        var decomposer = new StaticDecomposition<T>();
        Uni<DagPlan<TaskNode.LeafTask<T>>> result = resolvePlan(children.get(0), decomposer, ctx);
        for (int i = 1; i < children.size(); i++) {
            var sub = children.get(i);
            result = result.flatMap(prev ->
                    resolvePlan(sub, decomposer, ctx)
                            .map(next -> DagPlan.sequentialMerge(List.of(prev, next))));
        }
        return result;
    }

    private static <T> Uni<DagPlan<TaskNode.LeafTask<T>>> resolvePlan(
            TaskNode<T> node, StaticDecomposition<T> decomposer, DecompositionContext<T> ctx) {
        if (node instanceof TaskNode.LeafTask<T> leaf) {
            return Uni.createFrom().item(DagPlan.singleton(leaf));
        }
        return decomposer.decompose(node, ctx);
    }
}
