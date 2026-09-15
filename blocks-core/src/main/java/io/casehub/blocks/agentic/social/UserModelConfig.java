package io.casehub.blocks.agentic.social;

import java.time.Duration;
import java.util.Objects;

public record UserModelConfig(
        int minSignalsForSynthesis,
        Duration synthesisCooldown,
        double decayRate,
        double positiveWeight,
        double negativeWeight,
        RelationshipStageConfig stageConfig,
        Duration expectedTickInterval,
        Duration evictionTimeout,
        String memoryDomain,
        String caseType,
        int maxObservationsInPrompt) {

    public UserModelConfig {
        Objects.requireNonNull(synthesisCooldown, "synthesisCooldown required");
        Objects.requireNonNull(stageConfig, "stageConfig required");
        Objects.requireNonNull(expectedTickInterval, "expectedTickInterval required");
        Objects.requireNonNull(evictionTimeout, "evictionTimeout required");
        Objects.requireNonNull(memoryDomain, "memoryDomain required");
        Objects.requireNonNull(caseType, "caseType required");
        if (minSignalsForSynthesis < 1)
            throw new IllegalArgumentException("minSignalsForSynthesis must be >= 1");
        if (decayRate < 0.0 || decayRate > 1.0)
            throw new IllegalArgumentException("decayRate must be in [0,1]");
        if (maxObservationsInPrompt < 1)
            throw new IllegalArgumentException("maxObservationsInPrompt must be >= 1");
    }

    public static UserModelConfig defaults() {
        return new UserModelConfig(
                5,
                Duration.ofHours(1),
                0.01,
                1.0,
                0.5,
                RelationshipStageConfig.defaults(),
                Duration.ofHours(1),
                Duration.ofDays(7),
                "user-model",
                "user-profile",
                50);
    }
}
