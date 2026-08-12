package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class HeuristicDecomposition<T> implements DecompositionStrategy<T> {

    private static final System.Logger LOG = System.getLogger(HeuristicDecomposition.class.getName());

    private final DecompositionHeuristic<T> heuristic;

    public HeuristicDecomposition(DecompositionHeuristic<T> heuristic) {
        this.heuristic = Objects.requireNonNull(heuristic, "heuristic");
    }

    @Override
    public String id() {
        return "heuristic";
    }

    @Override
    public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> task,
                                                   DecompositionContext<T> context) {
        if (task instanceof TaskNode.LeafTask<T> leaf) {
            return DagPlan.singleton(leaf);
        }

        var ct = (TaskNode.CompoundTask<T>) task;
        var eligible = ct.methods().stream()
                         .filter(m -> m.guard().test(context.state()))
                         .toList();

        if (eligible.isEmpty()) {
            throw new NoMethodMatchedException(ct.name(), ct.methods().size());
        }

        var enrichedCtx = enrichContext(context);

        if (eligible.size() == 1) {
            return eligible.get(0).strategy().decompose(ct, enrichedCtx);
        }

        var scored = heuristic.evaluate(ct, eligible, context);
        var ranked = scored.stream()
                           .sorted(Comparator.comparingDouble(ScoredMethod<T>::score).reversed())
                           .map(ScoredMethod::method)
                           .toList();
        return tryRanked(ranked, ct, enrichedCtx);
    }

    private DagPlan<TaskNode.LeafTask<T>> tryRanked(List<DecompositionMethod<T>> ranked,
                                                    TaskNode.CompoundTask<T> ct,
                                                    DecompositionContext<T> ctx) {
        for (int i = 0; i < ranked.size(); i++) {
            try {
                return ranked.get(i).strategy().decompose(ct, ctx);
            } catch (NoMethodMatchedException e) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Method {0}/{1} failed for ''{2}'' — trying next",
                        i + 1, ranked.size(), ct.name());
            }
        }
        throw new NoMethodMatchedException(ct.name(), ranked.size());
    }

    private DecompositionContext<T> enrichContext(DecompositionContext<T> context) {
        if (context instanceof AgenticDecompositionContext<T> ac) {
            return new AgenticDecompositionContext<>(ac.state(), ac.agents(), ac.depth(),
                    ac.staticFailureHint(), ac.subtaskDescription(), ac.parentGoal(), ac.siblingNames(), this,
                    ac.planningConstraints());
        }
        return context;
    }
}
