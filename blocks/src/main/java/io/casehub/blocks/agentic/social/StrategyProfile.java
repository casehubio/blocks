package io.casehub.blocks.agentic.social;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StrategyProfile(
        String agentId,
        String tenantId,
        Map<String, Double> dimensions,
        List<String> guidelines,
        Instant lastReflection,
        int evidenceCount) {

    public StrategyProfile {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(dimensions, "dimensions required");
        Objects.requireNonNull(guidelines, "guidelines required");
        Objects.requireNonNull(lastReflection, "lastReflection required");
        dimensions = Map.copyOf(dimensions);
        guidelines = List.copyOf(guidelines);
    }

    public String toPromptSection() {
        if (guidelines.isEmpty()) return "";
        var sb = new StringBuilder("## Interaction Strategy\n\n");
        for (String guideline : guidelines) {
            sb.append("- ").append(guideline).append('\n');
        }
        return sb.toString();
    }
}
