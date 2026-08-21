package io.casehub.blocks.agentic.social.drive;

import java.util.Map;
import java.util.Objects;

public record DriveConfig(
        Map<DriveAxis, Double> axisWeights,
        double changeThreshold,
        double moodPleasureModulation,
        double moodArousalModulation,
        double personalityModulationStrength,
        double maxIntensity,
        double minIntensity) {
    public DriveConfig {
        Objects.requireNonNull(axisWeights, "axisWeights required");
        axisWeights = Map.copyOf(axisWeights);
        if (changeThreshold < 0.0 || changeThreshold > 1.0)
            throw new IllegalArgumentException("changeThreshold must be in [0.0, 1.0]");
        if (maxIntensity < minIntensity)
            throw new IllegalArgumentException("maxIntensity must be >= minIntensity");
        if (minIntensity < 0.0) throw new IllegalArgumentException("minIntensity must be >= 0.0");
        if (maxIntensity > 1.0) throw new IllegalArgumentException("maxIntensity must be <= 1.0");
    }

    public static DriveConfig defaults() {
        return new DriveConfig(
                Map.of(DriveAxis.CURIOSITY, 1.0, DriveAxis.COMPETENCE, 1.0,
                       DriveAxis.AFFILIATION, 1.0, DriveAxis.AUTONOMY, 1.0),
                0.05, 0.3, 0.2, 0.25, 1.0, 0.0);
    }
}
