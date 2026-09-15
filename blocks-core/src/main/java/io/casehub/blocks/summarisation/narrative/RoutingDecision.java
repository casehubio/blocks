package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RoutingDecision(
        String caseId, String stepName, Instant timestamp,
        String selectedAgentId, String strategyId, double score,
        List<String> candidateIds, @Nullable String reason
) implements DecisionSignal {
    public RoutingDecision {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(selectedAgentId);
        Objects.requireNonNull(strategyId);
        if (score < 0.0 || score > 1.0)
            throw new IllegalArgumentException("score must be in [0, 1]");
        candidateIds = List.copyOf(candidateIds);
    }
}
