package io.casehub.blocks.summarisation.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record StepOutcome(
        String caseId, String stepName, Instant timestamp,
        String status, @Nullable String workerId,
        @Nullable String errorMessage, Duration elapsed
) implements DecisionSignal {
    public StepOutcome {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(status);
        Objects.requireNonNull(elapsed);
    }
}
