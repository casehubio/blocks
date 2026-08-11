package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;


import java.util.List;

@FunctionalInterface
public interface DecompositionHeuristic<T> {
    List<ScoredMethod<T>> evaluate(
            TaskNode.CompoundTask<T> task,
            List<DecompositionMethod<T>> methods,
            DecompositionContext<T> context);
}
