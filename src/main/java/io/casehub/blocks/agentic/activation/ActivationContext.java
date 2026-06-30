package io.casehub.blocks.agentic.activation;

import io.casehub.blocks.agentic.AgentRef;

import java.util.Optional;

public record ActivationContext<T>(
        Object event,
        T state,
        AgentRef agent,
        int activationCount,
        Optional<Object> lastAggregationResult,
        int consecutiveIdleActivations
) {}
