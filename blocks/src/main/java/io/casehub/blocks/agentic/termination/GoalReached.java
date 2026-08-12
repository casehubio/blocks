package io.casehub.blocks.agentic.termination;

import java.util.function.Predicate;

public class GoalReached<T> implements TerminationCondition<T> {

    private final Predicate<T> goalPredicate;

    public GoalReached(Predicate<T> goalPredicate) {
        this.goalPredicate = goalPredicate;
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<T> context) {
        if (goalPredicate.test(context.state())) {
            return new TerminationDecision.Complete(context.state());
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
