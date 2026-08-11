package io.casehub.blocks.prompt;

import java.time.Duration;
import java.util.Objects;

public record SafetyConfig(
        double qualityFloor,
        int maxExperimentCycles,
        Duration maxExperimentAge,
        int circuitBreakerThreshold,
        boolean enabled) {

    public SafetyConfig {
        if (qualityFloor < 0 || qualityFloor > 1)
            throw new IllegalArgumentException("qualityFloor must be in [0, 1]");
        if (maxExperimentCycles <= 0)
            throw new IllegalArgumentException("maxExperimentCycles must be > 0");
        Objects.requireNonNull(maxExperimentAge, "maxExperimentAge");
        if (circuitBreakerThreshold <= 0)
            throw new IllegalArgumentException("circuitBreakerThreshold must be > 0");
    }

    public static SafetyConfig defaults() {
        return new SafetyConfig(0.3, 5, Duration.ofDays(30), 5, true);
    }
}
