package io.casehub.blocks.agentic.personality;

import java.time.Duration;

public record InnerLifeConfig(
        double motivationThreshold,
        ContentQualityGate contentQualityGate,
        int maxReflectionSources,
        int maxObservationsInPrompt,
        Duration windowDuration,
        Duration evictionTimeout) {

    public static final double DEFAULT_MOTIVATION_THRESHOLD = 0.6;
    public static final int DEFAULT_MAX_REFLECTION_SOURCES = 10;
    public static final int DEFAULT_MAX_OBSERVATIONS_IN_PROMPT = 50;
    public static final Duration DEFAULT_WINDOW_DURATION = Duration.ofHours(1);
    public static final Duration DEFAULT_EVICTION_TIMEOUT = Duration.ofHours(24);

    public InnerLifeConfig {
        if (motivationThreshold < 0.0 || motivationThreshold > 1.0) {
            throw new IllegalArgumentException("motivationThreshold must be in [0.0, 1.0]");
        }
        if (contentQualityGate == null) {
            throw new IllegalArgumentException("contentQualityGate must not be null");
        }
        if (maxReflectionSources <= 0) {
            throw new IllegalArgumentException("maxReflectionSources must be positive");
        }
        if (maxObservationsInPrompt <= 0) {
            throw new IllegalArgumentException("maxObservationsInPrompt must be positive");
        }
    }

    public static InnerLifeConfig defaults() {
        return new InnerLifeConfig(
                DEFAULT_MOTIVATION_THRESHOLD,
                ContentQualityGate.defaults(),
                DEFAULT_MAX_REFLECTION_SOURCES,
                DEFAULT_MAX_OBSERVATIONS_IN_PROMPT,
                DEFAULT_WINDOW_DURATION,
                DEFAULT_EVICTION_TIMEOUT);
    }
}
