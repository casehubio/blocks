package io.casehub.blocks.agentic.personality;

import java.time.Duration;

public record ContentQualityGate(double noveltyThreshold,
                                  int minObservations,
                                  Duration quietPeriodBypass) {

    public static final double DEFAULT_NOVELTY_THRESHOLD = 0.3;
    public static final int DEFAULT_MIN_OBSERVATIONS = 3;
    public static final Duration DEFAULT_QUIET_PERIOD = Duration.ofMinutes(30);

    public ContentQualityGate {
        if (noveltyThreshold < 0.0 || noveltyThreshold > 1.0) {
            throw new IllegalArgumentException("noveltyThreshold must be in [0.0, 1.0]");
        }
        if (minObservations < 0) {
            throw new IllegalArgumentException("minObservations must be >= 0");
        }
        if (quietPeriodBypass == null) {
            throw new IllegalArgumentException("quietPeriodBypass must not be null");
        }
    }

    public static ContentQualityGate defaults() {
        return new ContentQualityGate(DEFAULT_NOVELTY_THRESHOLD,
                DEFAULT_MIN_OBSERVATIONS, DEFAULT_QUIET_PERIOD);
    }
}
