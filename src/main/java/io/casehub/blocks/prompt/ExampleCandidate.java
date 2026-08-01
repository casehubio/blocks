package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record ExampleCandidate(
        String input,
        String output,
        String outcome,
        double qualityScore,
        double similarityScore,
        @Nullable String variantId,
        Instant occurredAt) {

    public ExampleCandidate {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
