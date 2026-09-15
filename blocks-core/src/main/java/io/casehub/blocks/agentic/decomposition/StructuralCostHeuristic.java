package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;

import java.util.List;

public class StructuralCostHeuristic<T> implements DecompositionHeuristic<T> {

    private final double opaqueCost;

    public StructuralCostHeuristic() {
        this(1.0);
    }

    public StructuralCostHeuristic(double opaqueCost) {
        if (opaqueCost < 0) throw new IllegalArgumentException("opaqueCost must be non-negative");
        this.opaqueCost = opaqueCost;
    }

    @Override
    public List<ScoredMethod<T>> evaluate(TaskNode.CompoundTask<T> task,
                                          List<DecompositionMethod<T>> methods,
                                          DecompositionContext<T> context) {
        return methods.stream()
                      .map(m -> new ScoredMethod<>(m, -estimateCost(m)))
                      .toList();
    }

    private double estimateCost(DecompositionMethod<T> method) {
        if (method.strategy() instanceof SequenceStrategy<T> seq) {
            return seq.children().stream()
                    .mapToDouble(this::estimateNodeCost)
                    .sum();
        }
        return opaqueCost;
    }

    private double estimateNodeCost(TaskNode<T> node) {
        if (node instanceof TaskNode.LeafTask<T>) {
            return 1.0;
        }
        if (node instanceof TaskNode.CompoundTask<T> ct) {
            return ct.methods().stream()
                    .mapToDouble(this::estimateCost)
                    .min()
                    .orElse(opaqueCost);
        }
        return opaqueCost;
    }
}
