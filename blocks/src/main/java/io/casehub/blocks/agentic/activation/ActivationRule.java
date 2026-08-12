package io.casehub.blocks.agentic.activation;

public interface ActivationRule<T> {
    boolean shouldActivate(ActivationContext<T> context);
}
