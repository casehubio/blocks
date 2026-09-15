package io.casehub.blocks.agentic.social;

@FunctionalInterface
public interface InteractionMapper {
    CognitiveImpact mapInteraction(String agentId, String targetId,
                                    String interactionType);
}
