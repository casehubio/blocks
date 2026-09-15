package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DecisionNarrative(
        String caseId, List<String> stepNames,
        String explanation, List<String> evidenceSources,
        double confidence, Instant producedAt
) {
    public DecisionNarrative {
        Objects.requireNonNull(caseId);
        stepNames = List.copyOf(stepNames);
        Objects.requireNonNull(explanation);
        evidenceSources = List.copyOf(evidenceSources);
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        Objects.requireNonNull(producedAt);
    }
}
