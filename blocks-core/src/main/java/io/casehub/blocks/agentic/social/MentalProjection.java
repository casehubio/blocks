package io.casehub.blocks.agentic.social;

import java.util.Objects;

public record MentalProjection(
        String conditionKey,
        boolean value,
        double confidence,
        BdiDimension dimension) {
    public MentalProjection {
        Objects.requireNonNull(conditionKey, "conditionKey required");
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        Objects.requireNonNull(dimension, "dimension required");
    }
}
