package io.casehub.blocks.agentic.termination;

public class MaxIterationsTermination<T> implements TerminationCondition<T> {

    private final int maxIterations;

    public MaxIterationsTermination(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<T> context) {
        if (context.iterationCount() >= maxIterations) {
            return new TerminationDecision.Complete("Max iterations reached");
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
