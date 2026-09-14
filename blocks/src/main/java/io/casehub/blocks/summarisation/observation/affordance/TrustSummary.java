package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record TrustSummary(String subjectName, TrustLevel level, @Nullable String reason) {
    public TrustSummary {
        Objects.requireNonNull(subjectName);
        Objects.requireNonNull(level);
    }
}
