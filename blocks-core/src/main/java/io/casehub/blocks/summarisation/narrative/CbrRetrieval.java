package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record CbrRetrieval(
        String caseId, String stepName, Instant timestamp,
        int retrievedCount, double topSimilarity,
        @Nullable String topCaseOutcome, @Nullable String domain
) implements DecisionSignal {
    public CbrRetrieval {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        if (retrievedCount < 0)
            throw new IllegalArgumentException("retrievedCount must be >= 0");
        if (topSimilarity < 0.0 || topSimilarity > 1.0)
            throw new IllegalArgumentException("topSimilarity must be in [0, 1]");
    }
}
