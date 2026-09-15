package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.eidos.api.GoalPriority;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record DriveGoalProposal(
        DriveAxis axis,
        String goalName,
        String goalDescription,
        String formationReason,
        double driveIntensity,
        @Nullable GoalPriority suggestedPriority,
        @Nullable Map<String, String> proposalAttributes) {
    public DriveGoalProposal {
        Objects.requireNonNull(axis, "axis required");
        Objects.requireNonNull(goalName, "goalName required");
        Objects.requireNonNull(goalDescription, "goalDescription required");
        Objects.requireNonNull(formationReason, "formationReason required");
        if (driveIntensity < 0.0 || driveIntensity > 1.0) {
            throw new IllegalArgumentException(
                    "driveIntensity must be in [0.0, 1.0], got " + driveIntensity);
        }
        proposalAttributes = proposalAttributes != null ? Map.copyOf(proposalAttributes) : null;
    }

    public DriveGoalProposal(DriveAxis axis, String goalName, String goalDescription,
                              String formationReason, double driveIntensity) {
        this(axis, goalName, goalDescription, formationReason, driveIntensity, null, null);
    }
}
