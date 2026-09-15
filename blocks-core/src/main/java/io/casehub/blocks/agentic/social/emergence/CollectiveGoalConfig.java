package io.casehub.blocks.agentic.social.emergence;

import java.time.Duration;
import java.util.Objects;

public record CollectiveGoalConfig(
        double alignmentThreshold,
        int minAlignedAgents,
        Duration cooldown) {
    public CollectiveGoalConfig {
        if (alignmentThreshold < 0.0 || alignmentThreshold > 1.0)
            throw new IllegalArgumentException("alignmentThreshold must be in [0, 1]");
        if (minAlignedAgents < 2)
            throw new IllegalArgumentException("minAlignedAgents must be >= 2");
        Objects.requireNonNull(cooldown);
    }

    public static CollectiveGoalConfig defaults() {
        return new CollectiveGoalConfig(0.6, 2, Duration.ofMinutes(60));
    }
}
