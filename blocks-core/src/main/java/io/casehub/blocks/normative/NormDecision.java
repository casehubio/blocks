package io.casehub.blocks.normative;

import java.time.Instant;
import java.util.Objects;

public record NormDecision<T>(
        String source,
        T decision,
        int priority,
        NormSpecificity specificity,
        Instant establishedAt
) {
    public NormDecision {
        Objects.requireNonNull(source);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(specificity);
        Objects.requireNonNull(establishedAt);
    }
}
