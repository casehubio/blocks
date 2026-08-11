package io.casehub.blocks.agentic.activation;

import io.smallrye.mutiny.Uni;

public interface ActivationRule<T> {
    Uni<Boolean> shouldActivate(ActivationContext<T> context);
}
