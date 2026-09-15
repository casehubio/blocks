package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.MemoryDomain;

import java.time.Duration;
import java.util.Objects;

public record StrategyLearningConfig(
        int minSignalsForConversationCase,
        int minCasesForReflection,
        int maxReflectionSources,
        int maxGuidelines,
        double defaultDimensionValue,
        int maxBufferSize,
        Duration staleStateTimeout,
        MemoryDomain memoryDomain,
        String engagementCaseType,
        String profileCaseType) {

    public StrategyLearningConfig {
        if (minSignalsForConversationCase < 1)
            throw new IllegalArgumentException("minSignalsForConversationCase must be >= 1");
        if (minCasesForReflection < 1)
            throw new IllegalArgumentException("minCasesForReflection must be >= 1");
        if (maxReflectionSources < 1)
            throw new IllegalArgumentException("maxReflectionSources must be >= 1");
        if (maxGuidelines < 1)
            throw new IllegalArgumentException("maxGuidelines must be >= 1");
        if (maxBufferSize < 1)
            throw new IllegalArgumentException("maxBufferSize must be >= 1");
        if (defaultDimensionValue < 0.0 || defaultDimensionValue > 1.0)
            throw new IllegalArgumentException("defaultDimensionValue must be in [0,1]");
        if (staleStateTimeout.isNegative() || staleStateTimeout.isZero())
            throw new IllegalArgumentException("staleStateTimeout must be positive");
        Objects.requireNonNull(memoryDomain, "memoryDomain required");
        Objects.requireNonNull(engagementCaseType, "engagementCaseType required");
        Objects.requireNonNull(profileCaseType, "profileCaseType required");
    }

    public static StrategyLearningConfig defaults() {
        return new StrategyLearningConfig(
                3, 5, 50, 10, 0.5, 100, Duration.ofHours(24),
                new MemoryDomain("strategy-learning"),
                "engagement-evidence", "strategy-profile");
    }
}
