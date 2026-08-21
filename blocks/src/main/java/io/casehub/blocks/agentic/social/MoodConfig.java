package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.mood.MoodBaseline;

import java.time.Duration;
import java.util.Objects;

public record MoodConfig(
        MoodBaseline baseline,
        Duration decayTimeConstant,
        double maxDisplacement,
        double moodInfluence,
        Duration staleStateTimeout) {

    public MoodConfig {
        Objects.requireNonNull(baseline, "baseline required");
        Objects.requireNonNull(decayTimeConstant, "decayTimeConstant required");
        Objects.requireNonNull(staleStateTimeout, "staleStateTimeout required");
        if (decayTimeConstant.isNegative() || decayTimeConstant.isZero())
            throw new IllegalArgumentException("decayTimeConstant must be positive");
        if (maxDisplacement <= 0.0 || maxDisplacement > 2.0)
            throw new IllegalArgumentException("maxDisplacement must be in (0, 2], got " + maxDisplacement);
        if (moodInfluence < 0.0 || moodInfluence > 1.0)
            throw new IllegalArgumentException("moodInfluence must be in [0, 1], got " + moodInfluence);
        if (staleStateTimeout.isNegative() || staleStateTimeout.isZero())
            throw new IllegalArgumentException("staleStateTimeout must be positive");
    }

    public static MoodConfig defaults() {
        return new MoodConfig(
                new MoodBaseline(0.0, 0.0, 0.0),
                Duration.ofHours(4),
                1.0,
                0.3,
                Duration.ofHours(24));
    }
}
