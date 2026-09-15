package io.casehub.blocks.agentic.social;

import java.time.Duration;
import java.util.Objects;

public record MentalModelConfig(
        Duration beliefHalfLife,
        Duration desireHalfLife,
        Duration intentionHalfLife,
        double confidenceFloor,
        double projectionFloor,
        int minSignalsForInference,
        Duration inferenceCooldown,
        int maxSignalsInPrompt,
        int maxBufferSize,
        Duration evictionTimeout,
        Duration expectedTickInterval,
        String memoryDomain,
        String caseType) {
    public MentalModelConfig {
        Objects.requireNonNull(beliefHalfLife, "beliefHalfLife required");
        Objects.requireNonNull(desireHalfLife, "desireHalfLife required");
        Objects.requireNonNull(intentionHalfLife, "intentionHalfLife required");
        Objects.requireNonNull(inferenceCooldown, "inferenceCooldown required");
        Objects.requireNonNull(evictionTimeout, "evictionTimeout required");
        Objects.requireNonNull(expectedTickInterval, "expectedTickInterval required");
        Objects.requireNonNull(memoryDomain, "memoryDomain required");
        Objects.requireNonNull(caseType, "caseType required");
    }

    public static MentalModelConfig defaults() {
        return new MentalModelConfig(
                Duration.ofDays(7), Duration.ofDays(1), Duration.ofHours(4),
                0.1, 0.3, 3, Duration.ofMinutes(5), 20, 100,
                Duration.ofHours(24), Duration.ofMinutes(1),
                "mental-model", "mental-model");
    }

    public Duration halfLifeFor(BdiDimension dimension) {
        return switch (dimension) {
            case BELIEF -> beliefHalfLife;
            case DESIRE -> desireHalfLife;
            case INTENTION -> intentionHalfLife;
        };
    }
}
