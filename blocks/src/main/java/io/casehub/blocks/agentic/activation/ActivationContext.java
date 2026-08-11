package io.casehub.blocks.agentic.activation;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.aggregation.AggregationResult;

import java.util.Optional;

public record ActivationContext<T>(
        Object event,
        T state,
        AgentRef agent,
        int activationCount,
        Optional<AggregationResult> lastAggregationResult,
        int consecutiveIdleActivations
) {}
