package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentRef;

public record AgentParticipant(
        AgentRef agentRef,
        String role,
        String systemPrompt
) {
    public String agentId() { return agentRef.name(); }
}
