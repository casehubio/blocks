package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DeliberationOutcome(
        String caseId, String stepName, Instant timestamp,
        String outcome, int rounds,
        @Nullable String convergenceState,
        List<String> participantIds
) implements DecisionSignal {
    public DeliberationOutcome {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(outcome);
        if (rounds < 0)
            throw new IllegalArgumentException("rounds must be >= 0");
        participantIds = List.copyOf(participantIds);
    }
}
