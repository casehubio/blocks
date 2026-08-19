package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.AgentDescriptor;

import java.time.Instant;

public record InitiationContext(
        Instant lastInitiationTimestamp,
        int initiationsInWindow,
        int consecutiveInitiationsWithoutResponse,
        AgentDescriptor descriptor) {}
