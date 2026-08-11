package io.casehub.blocks.agentic;

import io.casehub.eidos.api.AgentDescriptor;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A candidate for agent routing — pairs a dispatch reference with an optional identity profile.
 *
 * @param ref        the agent dispatch reference — never null
 * @param descriptor the agent's identity profile for routing metadata.
 *                   Nullable — pattern builders that accept bare {@link AgentRef}
 *                   create candidates without descriptors. Routing strategies
 *                   must handle null descriptors gracefully.
 */
public record RoutingCandidate(AgentRef ref, @Nullable AgentDescriptor descriptor) {
    public RoutingCandidate {
        Objects.requireNonNull(ref, "ref");
    }
}
