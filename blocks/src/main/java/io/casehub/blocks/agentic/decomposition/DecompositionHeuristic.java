package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;

import java.util.List;

@FunctionalInterface
public interface DecompositionHeuristic<T> {
    Uni<List<ScoredMethod<T>>> evaluate(
            TaskNode.CompoundTask<T> task,
            List<DecompositionMethod<T>> methods,
            DecompositionContext<T> context);
}
