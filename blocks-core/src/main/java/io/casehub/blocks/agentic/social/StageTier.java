package io.casehub.blocks.agentic.social;

import java.util.Objects;

public record StageTier(String name, double threshold) {
    public StageTier {
        Objects.requireNonNull(name, "name required");
        if (threshold < 0.0 || threshold > 1.0)
            throw new IllegalArgumentException("threshold must be in [0,1], got " + threshold);
    }
}
