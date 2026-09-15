package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;
import java.util.Objects;

public record ModelSelection(
        String caseId, String stepName, Instant timestamp,
        String modelId, String modelTier, String capabilityName,
        String vendor, String displayName
) implements DecisionSignal {
    public ModelSelection {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(modelId);
        Objects.requireNonNull(modelTier);
        Objects.requireNonNull(capabilityName);
        Objects.requireNonNull(vendor);
        Objects.requireNonNull(displayName);
    }
}
