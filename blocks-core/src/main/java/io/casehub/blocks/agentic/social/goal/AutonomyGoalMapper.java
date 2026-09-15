package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.agentic.social.drive.AutonomyDrive;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import org.jspecify.annotations.Nullable;

public class AutonomyGoalMapper implements DriveGoalMapper {

    private final AutonomyDrive autonomyDrive;
    private final double        confidenceFloor;

    public AutonomyGoalMapper(AutonomyDrive autonomyDrive, double confidenceFloor) {
        this.autonomyDrive   = autonomyDrive;
        this.confidenceFloor = confidenceFloor;
    }

    @Override
    public @Nullable DriveGoalProposal evaluate(String agentId, String tenantId,
                                                DriveIntensity intensity) {
        if (intensity.axis() != DriveAxis.AUTONOMY) {return null;}
        var snapshots = autonomyDrive.lastSnapshots();
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }

        MentalModelSnapshot mostMisaligned = null;
        int                 maxIntentions  = 0;

        for (var snapshot : snapshots) {
            int highConfidence = 0;
            for (var intention : snapshot.intentions()) {
                if (intention.confidence() >= confidenceFloor) {
                    highConfidence++;
                }
            }
            if (highConfidence > maxIntentions) {
                maxIntentions  = highConfidence;
                mostMisaligned = snapshot;
            }
        }

        if (mostMisaligned == null || maxIntentions == 0) {
            return null;
        }

        return new DriveGoalProposal(
                DriveAxis.AUTONOMY,
                "reassess-" + mostMisaligned.subjectId(),
                "Reassess intentions attributed to " + mostMisaligned.subjectId()
                + " (" + maxIntentions + " high-confidence intentions)",
                "autonomy: " + maxIntentions + " high-confidence intentions for "
                + mostMisaligned.subjectId(),
                intensity.intensity());
    }
}
