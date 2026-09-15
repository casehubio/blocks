package io.casehub.blocks.agentic.social.goal;

public record GoalEscalationConfig(
        double escalationSalienceThreshold,
        double minAxisAlignmentWeight,
        double crossAxisMinWeight,
        int minCrossAxisCount,
        int escalationCycles,
        int demotionCycles,
        int maxPrimaryDriveGoals) {

    public GoalEscalationConfig {
        if (escalationSalienceThreshold < 0.0 || escalationSalienceThreshold > 1.0)
            throw new IllegalArgumentException("escalationSalienceThreshold must be in [0, 1]");
        if (minAxisAlignmentWeight < 0.0 || minAxisAlignmentWeight > 1.0)
            throw new IllegalArgumentException("minAxisAlignmentWeight must be in [0, 1]");
        if (crossAxisMinWeight < 0.0 || crossAxisMinWeight > 1.0)
            throw new IllegalArgumentException("crossAxisMinWeight must be in [0, 1]");
        if (minCrossAxisCount < 2)
            throw new IllegalArgumentException("minCrossAxisCount must be >= 2");
        if (escalationCycles < 1)
            throw new IllegalArgumentException("escalationCycles must be >= 1");
        if (demotionCycles < 1)
            throw new IllegalArgumentException("demotionCycles must be >= 1");
        if (maxPrimaryDriveGoals < 0)
            throw new IllegalArgumentException("maxPrimaryDriveGoals must be >= 0");
    }

    public static GoalEscalationConfig defaults() {
        return new GoalEscalationConfig(0.6, 0.3, 0.3, 2, 2, 2, 1);
    }
}
