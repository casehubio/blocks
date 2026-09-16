package io.casehub.blocks.agentic.social;

@FunctionalInterface
public interface RelationshipStageConfigProvider {
    RelationshipStageConfig forAgent(String agentId);
}
