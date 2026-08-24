package io.casehub.blocks.agentic.social.emergence;

public record NormDetectionConfig(
        int minObservationsForNorm,
        double establishedThreshold,
        double decliningThreshold,
        int minAgentsForNorm) {
    public NormDetectionConfig {
        if (minObservationsForNorm < 1)
            throw new IllegalArgumentException("minObservationsForNorm must be >= 1");
        if (establishedThreshold < 0.0 || establishedThreshold > 1.0)
            throw new IllegalArgumentException("establishedThreshold must be in [0, 1]");
        if (decliningThreshold < 0.0 || decliningThreshold > 1.0)
            throw new IllegalArgumentException("decliningThreshold must be in [0, 1]");
        if (minAgentsForNorm < 1)
            throw new IllegalArgumentException("minAgentsForNorm must be >= 1");
    }

    public static NormDetectionConfig defaults() {
        return new NormDetectionConfig(10, 0.7, 0.4, 2);
    }
}
