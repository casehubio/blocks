package io.casehub.blocks.agentic.social.goal;

import io.casehub.eidos.api.GoalPriority;

import java.util.Objects;

public record PriorityAdjustment(
        String goalName,
        GoalPriority newPriority,
        String reason) {
    public PriorityAdjustment {
        Objects.requireNonNull(goalName);
        Objects.requireNonNull(newPriority);
        Objects.requireNonNull(reason);
    }
}
