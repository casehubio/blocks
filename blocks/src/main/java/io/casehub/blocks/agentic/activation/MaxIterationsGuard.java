package io.casehub.blocks.agentic.activation;

public class MaxIterationsGuard<T> implements ActivationRule<T> {

    private final int maxIterations;

    public MaxIterationsGuard(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public boolean shouldActivate(ActivationContext<T> context) {
        return context.activationCount() < maxIterations;
    }
}
