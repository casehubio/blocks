package io.casehub.blocks.agentic.social.drive;

import java.util.Objects;

public record DriveIntensity(DriveAxis axis, double intensity, String trigger) {
    public DriveIntensity {
        Objects.requireNonNull(axis, "axis required");
        Objects.requireNonNull(trigger, "trigger required");
        if (intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("intensity must be in [0.0, 1.0], got " + intensity);
        }
    }
}
