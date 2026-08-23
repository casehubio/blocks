package io.casehub.blocks.agentic.social.goal;

import java.time.Duration;

public record GoalProposalConfig(
        double proposalThreshold,
        double relevanceThreshold,
        int maxDriveGoals,
        Duration staleAfter,
        Duration cooldown,
        int failureAbandonmentThreshold) {

    public GoalProposalConfig {
        if (proposalThreshold < 0.0 || proposalThreshold > 1.0)
            throw new IllegalArgumentException("proposalThreshold must be in [0.0, 1.0]");
        if (relevanceThreshold < 0.0 || relevanceThreshold > 1.0)
            throw new IllegalArgumentException("relevanceThreshold must be in [0.0, 1.0]");
        if (maxDriveGoals < 0)
            throw new IllegalArgumentException("maxDriveGoals must be >= 0");
        if (failureAbandonmentThreshold < 1)
            throw new IllegalArgumentException("failureAbandonmentThreshold must be >= 1");
    }

    public static GoalProposalConfig defaults() {
        return new GoalProposalConfig(
                0.4,
                0.2,
                3,
                Duration.ofMinutes(120),
                Duration.ofMinutes(60),
                5);
    }
}
