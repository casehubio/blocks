package io.casehub.blocks.agentic.coalition;

import java.util.Set;

public record CoalitionScore(
        double score,
        double capabilityCoverage,
        Set<String> missingCapabilities,
        String reason
) {
    public CoalitionScore {
        missingCapabilities = Set.copyOf(missingCapabilities);
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("Score must be in [0.0, 1.0]");
        if (capabilityCoverage < 0.0 || capabilityCoverage > 1.0) throw new IllegalArgumentException("Coverage must be in [0.0, 1.0]");
    }

    public boolean isViable() {
        return missingCapabilities.isEmpty();
    }
}
