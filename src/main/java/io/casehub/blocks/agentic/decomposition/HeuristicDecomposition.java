package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;

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
    public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> task,
                                                         DecompositionContext<T> context) {
        if (task instanceof TaskNode.LeafTask<T> leaf) {
            return Uni.createFrom().item(DagPlan.singleton(leaf));
        }

        var ct = (TaskNode.CompoundTask<T>) task;
        var eligible = ct.methods().stream()
                .filter(m -> m.guard().test(context.state()))
                .toList();

        if (eligible.isEmpty()) {
            return Uni.createFrom().failure(
                    new NoMethodMatchedException(ct.name(), ct.methods().size()));
        }

        var enrichedCtx = enrichContext(context);

        if (eligible.size() == 1) {
            return eligible.get(0).strategy().decompose(ct, enrichedCtx);
        }

        return heuristic.evaluate(ct, eligible, context)
                .flatMap(scored -> {
                    var ranked = scored.stream()
                            .sorted(Comparator.comparingDouble(ScoredMethod<T>::score).reversed())
                            .map(ScoredMethod::method)
                            .toList();
                    return tryRanked(ranked, 0, ct, enrichedCtx);
                });
    }

    private Uni<DagPlan<TaskNode.LeafTask<T>>> tryRanked(List<DecompositionMethod<T>> ranked,
                                                          int index,
                                                          TaskNode.CompoundTask<T> ct,
                                                          DecompositionContext<T> ctx) {
        if (index >= ranked.size()) {
            return Uni.createFrom().failure(
                    new NoMethodMatchedException(ct.name(), ranked.size()));
        }

        return ranked.get(index).strategy().decompose(ct, ctx)
                .onFailure(NoMethodMatchedException.class)
                .recoverWithUni(failure -> {
                    LOG.log(System.Logger.Level.DEBUG,
                            "Method {0}/{1} failed for ''{2}'' — trying next",
                            index + 1, ranked.size(), ct.name());
                    return tryRanked(ranked, index + 1, ct, ctx);
                });
    }

    private DecompositionContext<T> enrichContext(DecompositionContext<T> context) {
        if (context instanceof AgenticDecompositionContext<T> ac) {
            return new AgenticDecompositionContext<>(ac.state(), ac.agents(), ac.depth(),
                    ac.staticFailureHint(), ac.subtaskDescription(), ac.parentGoal(), ac.siblingNames(), this);
        }
        return context;
    }
}
