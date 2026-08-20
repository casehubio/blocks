package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;

import java.util.List;

public interface TraitPressureSource<E> {
    Class<E> eventType();

    List<TraitActivation> translate(E event, AgentDescriptor descriptor);
}
