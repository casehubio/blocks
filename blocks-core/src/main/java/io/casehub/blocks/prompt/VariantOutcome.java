package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record VariantOutcome(
        String variantId,
        String signatureId,
        String outcome,
        double similarityScore,
        @Nullable Duration executionDuration,
        Instant occurredAt) {

    public VariantOutcome {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(signatureId, "signatureId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
