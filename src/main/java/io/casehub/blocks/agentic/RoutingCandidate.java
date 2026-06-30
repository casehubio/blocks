package io.casehub.blocks.agentic;

import io.casehub.eidos.api.AgentDescriptor;

public record RoutingCandidate(AgentRef ref, AgentDescriptor descriptor) {}
