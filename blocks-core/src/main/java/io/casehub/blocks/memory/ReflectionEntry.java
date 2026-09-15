package io.casehub.blocks.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReflectionEntry(String agentId, String tenantId, String insight,
                                Instant generatedAt, List<String> sourceCaseIds) {
    public ReflectionEntry {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(insight, "insight required");
        Objects.requireNonNull(generatedAt, "generatedAt required");
        sourceCaseIds = sourceCaseIds != null ? List.copyOf(sourceCaseIds) : List.of();
    }
}
