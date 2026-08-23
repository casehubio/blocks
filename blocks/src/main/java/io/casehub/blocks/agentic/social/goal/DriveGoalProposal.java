package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import java.util.Objects;

public record DriveGoalProposal(
        DriveAxis axis,
        String goalName,
        String goalDescription,
        String formationReason,
        double driveIntensity) {
    public DriveGoalProposal {
        Objects.requireNonNull(axis, "axis required");
        Objects.requireNonNull(goalName, "goalName required");
        Objects.requireNonNull(goalDescription, "goalDescription required");
        Objects.requireNonNull(formationReason, "formationReason required");
        if (driveIntensity < 0.0 || driveIntensity > 1.0) {
            throw new IllegalArgumentException(
                    "driveIntensity must be in [0.0, 1.0], got " + driveIntensity);
        }
    }
}
