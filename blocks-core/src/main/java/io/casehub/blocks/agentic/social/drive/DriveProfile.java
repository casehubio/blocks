package io.casehub.blocks.agentic.social.drive;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record DriveProfile(
        String agentId,
        String tenantId,
        Map<DriveAxis, DriveIntensity> drives,
        double compositeMotivation,
        DriveAxis dominantDrive,
        Instant evaluatedAt) {
    public DriveProfile {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(drives, "drives required");
        Objects.requireNonNull(dominantDrive, "dominantDrive required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        if (compositeMotivation < 0.0 || compositeMotivation > 1.0) {
            throw new IllegalArgumentException(
                    "compositeMotivation must be in [0.0, 1.0], got " + compositeMotivation);
        }
        drives = Map.copyOf(drives);
    }
}
