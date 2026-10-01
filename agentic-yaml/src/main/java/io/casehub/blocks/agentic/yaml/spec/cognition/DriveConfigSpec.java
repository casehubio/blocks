package io.casehub.blocks.agentic.yaml.spec.cognition;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public record DriveConfigSpec(
        @Nullable Map<DriveAxis, Double> axisWeights,
        @Nullable Double changeThreshold,
        @Nullable Double moodPleasureModulation,
        @Nullable Double moodArousalModulation,
        @Nullable Double personalityModulationStrength,
        @Nullable Double maxIntensity,
        @Nullable Double minIntensity,
        @Nullable Double affiliationDecayThreshold,
        @Nullable Duration affiliationStaleDuration,
        @Nullable Double autonomyConfidenceFloor,
        @Nullable Double narrativeModulationStrength) {}
