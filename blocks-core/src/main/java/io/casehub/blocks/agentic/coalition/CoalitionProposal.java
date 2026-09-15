package io.casehub.blocks.agentic.coalition;

import io.casehub.blocks.agentic.AgentRef;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CoalitionProposal(
        String taskId,
        Set<String> requiredCapabilities,
        List<AgentRef> proposedMembers
) {
    public CoalitionProposal {
        Objects.requireNonNull(taskId);
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        proposedMembers = List.copyOf(proposedMembers);
    }

    public Set<String> coveredCapabilities() {
        // Delegates to AgentCardSupport or member metadata — here we return
        // the member names as a proxy. Real implementations use worker capability tags.
        return Set.copyOf(proposedMembers.stream().map(AgentRef::name).toList());
    }
}
