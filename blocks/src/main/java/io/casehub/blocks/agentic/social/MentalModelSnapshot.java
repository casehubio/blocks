package io.casehub.blocks.agentic.social;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MentalModelSnapshot(
        String agentId,
        String subjectId,
        String tenantId,
        List<AttributedState> beliefs,
        List<AttributedState> desires,
        List<AttributedState> intentions,
        Instant lastSignal,
        @Nullable Instant lastInference,
        Instant snapshotCreated) {
    public MentalModelSnapshot {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(subjectId, "subjectId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(beliefs, "beliefs required");
        Objects.requireNonNull(desires, "desires required");
        Objects.requireNonNull(intentions, "intentions required");
        Objects.requireNonNull(lastSignal, "lastSignal required");
        Objects.requireNonNull(snapshotCreated, "snapshotCreated required");
        beliefs = List.copyOf(beliefs);
        desires = List.copyOf(desires);
        intentions = List.copyOf(intentions);
    }
}
