package io.casehub.blocks.memory;

public record RetentionConfig(double retentionThreshold,
                              double confidenceWeight, double recencyWeight,
                              double scopeWeight, double trustWeight) {
    public RetentionConfig {
        if (retentionThreshold < 0.0 || retentionThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "retentionThreshold must be in [0,1], got " + retentionThreshold);
        }
        if (confidenceWeight < 0 || recencyWeight < 0 || scopeWeight < 0 || trustWeight < 0) {
            throw new IllegalArgumentException("weights must be >= 0");
        }
        if (confidenceWeight + recencyWeight + scopeWeight + trustWeight <= 0) {
            throw new IllegalArgumentException("at least one weight must be > 0");
        }
    }

    public static final RetentionConfig DEFAULT =
            new RetentionConfig(0.1, 1.0, 1.0, 0.5, 0.5);
}
