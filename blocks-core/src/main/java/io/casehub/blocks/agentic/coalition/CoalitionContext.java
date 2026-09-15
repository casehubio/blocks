package io.casehub.blocks.agentic.coalition;

import io.casehub.blocks.agentic.AgentRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CoalitionContext(
        List<AgentRef> availableAgents,
        Map<String, Set<String>> agentCapabilities
) {
    public CoalitionContext {
        availableAgents = List.copyOf(availableAgents);
        Objects.requireNonNull(agentCapabilities);
    }

    public Set<String> capabilitiesOf(AgentRef agent) {
        return agentCapabilities.getOrDefault(agent.name(), Set.of());
    }
}
