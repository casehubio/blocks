package io.casehub.blocks.annotations.runtime;

import io.casehub.blocks.agentic.model.PatternType;

import java.util.List;
import java.util.Map;

public record PatternDescriptor(
        PatternType patternType,
        Map<String, Object> attributes,
        List<AgentParticipant> participants,
        String beanName
) {
    public record AgentParticipant(
            String label,
            String role,
            String systemPrompt,
            String agentId,
            boolean isJudge
    ) {}
}
