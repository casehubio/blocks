package io.casehub.blocks.agentic.social.narrative;

import java.time.Duration;
import java.util.Objects;

public record NarrativeSynthesisGate(
        int minNewReflections,
        double noveltyThreshold,
        Duration quietPeriodBypass) {
    public NarrativeSynthesisGate {
        if (minNewReflections < 1)
            throw new IllegalArgumentException("minNewReflections must be >= 1");
        if (noveltyThreshold < 0.0 || noveltyThreshold > 1.0)
            throw new IllegalArgumentException("noveltyThreshold must be in [0, 1]");
        Objects.requireNonNull(quietPeriodBypass);
    }

    public static NarrativeSynthesisGate defaults() {
        return new NarrativeSynthesisGate(5, 0.3, Duration.ofMinutes(120));
    }
}
