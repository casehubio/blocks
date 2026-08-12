package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * SHOP-style forward-reasoning decomposition. Applies {@link PrimitiveTask#effect()}
 * to a projected copy of state during planning so downstream method guards see
 * the effects of upstream tasks.
 *
 * <p>Drop-in replacement for {@link StaticDecomposition} on pure-symbolic trees.
 * The original state is never modified — a copy is made via the supplied copier.
 */
public class ForwardReasoningDecomposition<T> implements DecompositionStrategy<T> {

    private final UnaryOperator<T> stateCopier;

    public ForwardReasoningDecomposition(UnaryOperator<T> stateCopier) {
        this.stateCopier = Objects.requireNonNull(stateCopier);
    }

    @Override
    public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> task, DecompositionContext<T> ctx) {
        T                          projected = stateCopier.apply(ctx.state());
        List<TaskNode.LeafTask<T>> result    = new ArrayList<>();
        expand(task, projected, result);
        return DagPlan.sequence(result);
    }

    private void expand(TaskNode<T> node, T state, List<TaskNode.LeafTask<T>> result) {
        if (node instanceof TaskNode.LeafTask<T> leaf) {
            result.add(leaf);
            if (leaf instanceof PrimitiveTask<T> pt && pt.effect() != null) {
                pt.effect().accept(state);
            }
            return;
        }
        var ct = (TaskNode.CompoundTask<T>) node;
        for (var method : ct.methods()) {
            if (method.guard().test(state)) {
                expandMethod(method, ct, state, result);
                return;
            }
        }
        throw new NoMethodMatchedException(ct.name(), ct.methods().size());
    }

    private void expandMethod(DecompositionMethod<T> method, TaskNode.CompoundTask<T> ct,
                              T state, List<TaskNode.LeafTask<T>> result) {
        if (method.strategy() instanceof SequenceStrategy<T> seq) {
            for (var child : seq.children()) {
                expand(child, state, result);
            }
        } else {
            var ctx = new AgenticDecompositionContext<>(state, List.of(), 0);
            var plan = method.strategy().decompose(ct, ctx);
            for (var dagNode : plan.topologicalSort()) {
                result.add(dagNode.task());
                if (dagNode.task() instanceof PrimitiveTask<T> pt && pt.effect() != null) {
                    pt.effect().accept(state);
                }
            }
        }
    }
}
