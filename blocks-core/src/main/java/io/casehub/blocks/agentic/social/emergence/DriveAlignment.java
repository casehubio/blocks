package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record DriveAlignment(
        Set<String> agentIds,
        Map<DriveAxis, Double> alignmentPerAxis,
        double compositeAlignment,
        @Nullable DriveAxis dominantSharedAxis,
        Instant computedAt) {
    public DriveAlignment {
        agentIds = Set.copyOf(agentIds);
        alignmentPerAxis = Map.copyOf(alignmentPerAxis);
        if (compositeAlignment < 0.0 || compositeAlignment > 1.0)
            throw new IllegalArgumentException("compositeAlignment must be in [0, 1]");
        Objects.requireNonNull(computedAt);
    }
}
