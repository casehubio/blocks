package io.casehub.blocks.agentic.termination;

import io.smallrye.mutiny.Uni;

import java.util.function.Predicate;

public class GoalReached<T> implements TerminationCondition<T> {

    private final Predicate<T> goalPredicate;

    public GoalReached(Predicate<T> goalPredicate) {
        this.goalPredicate = goalPredicate;
    }

    @Override
    public Uni<TerminationDecision> evaluate(TerminationContext<T> context) {
        if (goalPredicate.test(context.state())) {
            return Uni.createFrom().item(new TerminationDecision.Complete(context.state()));
        }
        return Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
    }
}
