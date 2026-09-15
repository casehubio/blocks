package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;
import java.util.Objects;

public record TrustAssessment(
        String caseId, String stepName, Instant timestamp,
        String agentId, double trustScore, double threshold,
        boolean passed
) implements DecisionSignal {
    public TrustAssessment {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(agentId);
        if (trustScore < 0.0 || trustScore > 1.0)
            throw new IllegalArgumentException("trustScore must be in [0, 1]");
        if (threshold < 0.0 || threshold > 1.0)
            throw new IllegalArgumentException("threshold must be in [0, 1]");
    }
}
