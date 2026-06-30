package io.casehub.blocks.agentic.activation;

import io.smallrye.mutiny.Uni;

public class OnExplicitDispatch<T> implements ActivationRule<T> {

    @Override
    public Uni<Boolean> shouldActivate(ActivationContext<T> context) {
        return Uni.createFrom().item(true);
    }
}
