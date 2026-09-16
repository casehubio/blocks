package io.casehub.blocks.agentic.social;

@FunctionalInterface
public interface CognitionTickParticipant {
    void tick(CognitionTickContext context);
}
