package io.casehub.blocks.agentic.social.emergence;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record SocialNorm(
        String normId,
        String description,
        String behavioralPattern,
        double adherenceRate,
        int observationCount,
        Set<String> participatingAgents,
        Instant firstObserved,
        Instant lastObserved,
        NormStrength strength) {
    public SocialNorm {
        Objects.requireNonNull(normId);
        Objects.requireNonNull(description);
        Objects.requireNonNull(behavioralPattern);
        if (adherenceRate < 0.0 || adherenceRate > 1.0)
            throw new IllegalArgumentException("adherenceRate must be in [0, 1]");
        participatingAgents = Set.copyOf(participatingAgents);
        Objects.requireNonNull(firstObserved);
        Objects.requireNonNull(lastObserved);
        Objects.requireNonNull(strength);
    }
}
