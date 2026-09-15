package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record StepDecisionSummary(
        String caseId, String stepName,
        List<SignalDigest> signals,
        Instant from, Instant to
) {
    public StepDecisionSummary {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        signals = List.copyOf(signals);
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
    }
}
