package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;

import java.time.Instant;

public record InitiationContext(
        Instant lastInitiationTimestamp,
        int initiationsInWindow,
        int consecutiveInitiationsWithoutResponse,
        AgentDescriptor descriptor) {}
