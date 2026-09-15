package io.casehub.blocks.agentic.social;

import java.util.Map;
import java.util.Objects;

public record EngagementTrend(
        Map<String, TrendDirection> dimensionTrends,
        double responseRateTrajectory,
        int evidenceWindow) {
    public EngagementTrend {
        Objects.requireNonNull(dimensionTrends, "dimensionTrends required");
        dimensionTrends = Map.copyOf(dimensionTrends);
        if (evidenceWindow < 0) throw new IllegalArgumentException("evidenceWindow must be >= 0");
    }

    public enum TrendDirection { IMPROVING, STABLE, DECLINING }
}
