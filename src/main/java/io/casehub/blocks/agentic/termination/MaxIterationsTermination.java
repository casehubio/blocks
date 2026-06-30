package io.casehub.blocks.agentic.termination;

import io.smallrye.mutiny.Uni;

public class MaxIterationsTermination<T> implements TerminationCondition<T> {

    private final int maxIterations;

    public MaxIterationsTermination(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public Uni<TerminationDecision> evaluate(TerminationContext<T> context) {
        if (context.iterationCount() >= maxIterations) {
            return Uni.createFrom().item(new TerminationDecision.Complete("Max iterations reached"));
        }
        return Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
    }
}
