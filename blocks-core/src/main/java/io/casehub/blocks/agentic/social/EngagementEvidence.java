package io.casehub.blocks.agentic.social;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record EngagementEvidence(
        String agentId,
        String subjectId,
        String tenantId,
        @Nullable String conversationId,
        @Nullable String conversationSummary,
        int turnCount,
        double continuationRate,
        double avgResponseLength,
        double meanAffectShift,
        Map<String, Double> dimensionSnapshots,
        Instant recordedAt) {

    public EngagementEvidence {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(subjectId, "subjectId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(dimensionSnapshots, "dimensionSnapshots required");
        Objects.requireNonNull(recordedAt, "recordedAt required");
        dimensionSnapshots = Map.copyOf(dimensionSnapshots);
    }
}
