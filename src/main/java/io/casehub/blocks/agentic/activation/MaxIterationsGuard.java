package io.casehub.blocks.agentic.activation;

import io.smallrye.mutiny.Uni;

public class MaxIterationsGuard<T> implements ActivationRule<T> {

    private final int maxIterations;

    public MaxIterationsGuard(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public Uni<Boolean> shouldActivate(ActivationContext<T> context) {
        return Uni.createFrom().item(context.activationCount() < maxIterations);
    }
}
