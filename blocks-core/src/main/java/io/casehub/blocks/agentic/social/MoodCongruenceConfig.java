package io.casehub.blocks.agentic.social;

public record MoodCongruenceConfig(
        double arousalCongruenceFactor,
        double pleasureCongruenceFactor,
        double minThresholdMultiplier,
        double maxThresholdMultiplier) {

    public MoodCongruenceConfig {
        if (arousalCongruenceFactor < 0.0 || arousalCongruenceFactor > 1.0)
            throw new IllegalArgumentException("arousalCongruenceFactor must be in [0, 1], got " + arousalCongruenceFactor);
        if (pleasureCongruenceFactor < 0.0 || pleasureCongruenceFactor > 1.0)
            throw new IllegalArgumentException("pleasureCongruenceFactor must be in [0, 1], got " + pleasureCongruenceFactor);
        if (minThresholdMultiplier <= 0.0)
            throw new IllegalArgumentException("minThresholdMultiplier must be positive, got " + minThresholdMultiplier);
        if (maxThresholdMultiplier <= minThresholdMultiplier)
            throw new IllegalArgumentException("maxThresholdMultiplier must exceed minThresholdMultiplier");
    }

    public static MoodCongruenceConfig defaults() {
        return new MoodCongruenceConfig(0.5, 0.3, 0.3, 2.5);
    }
}
