package io.casehub.blocks.memory;

import java.util.Objects;

public record WeightedScorer(ConfidenceScorer scorer, double weight) {
    public WeightedScorer {
        Objects.requireNonNull(scorer, "scorer required");
        if (weight <= 0.0) {
            throw new IllegalArgumentException("weight must be positive, got " + weight);
        }
    }
}
