package io.casehub.blocks.agentic.social;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record UserProfile(
        String agentId,
        String subjectId,
        String tenantId,
        String relationshipStage,
        double familiarityScore,
        int totalInteractions,
        int positiveSignals,
        int negativeSignals,
        int neutralSignals,
        Instant lastInteraction,
        Instant profileCreated,
        @Nullable Instant lastSynthesised,
        @Nullable String communicationStyle,
        @Nullable String topicsOfInterest,
        @Nullable String preferences,
        @Nullable String synthesisNotes,
        Map<String, String> metadata) {

    public UserProfile {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(subjectId, "subjectId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(relationshipStage, "relationshipStage required");
        Objects.requireNonNull(lastInteraction, "lastInteraction required");
        Objects.requireNonNull(profileCreated, "profileCreated required");
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
    }
}
