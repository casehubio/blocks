package io.casehub.blocks.prompt;

public record OptimiserConfig(
        int maxExamples,
        double minQualityThreshold,
        int minOutcomeCount,
        int minVariantOutcomes) {

    public OptimiserConfig {
        if (maxExamples <= 0) throw new IllegalArgumentException("maxExamples must be > 0");
        if (minQualityThreshold < 0 || minQualityThreshold > 1)
            throw new IllegalArgumentException("minQualityThreshold must be in [0, 1]");
        if (minOutcomeCount <= 0) throw new IllegalArgumentException("minOutcomeCount must be > 0");
        if (minVariantOutcomes <= 0) throw new IllegalArgumentException("minVariantOutcomes must be > 0");
    }

    public static OptimiserConfig defaults() {
        return new OptimiserConfig(5, 0.7, 50, 20);
    }
}
