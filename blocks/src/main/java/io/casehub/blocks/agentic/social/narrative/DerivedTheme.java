package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DerivedTheme(
        String id,
        Instant from,
        @Nullable Instant to,
        List<String> thematicTags,
        String label,
        double salience,
        Map<DriveAxis, Double> axisModulationWeights,
        List<String> supportingFragmentIds
) implements NarrativeFragment {
    public DerivedTheme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(label);
        if (salience < 0.0 || salience > 1.0)
            throw new IllegalArgumentException("salience must be in [0, 1]");
        axisModulationWeights = Map.copyOf(axisModulationWeights);
        for (var w : axisModulationWeights.values()) {
            if (w < -1.0 || w > 1.0)
                throw new IllegalArgumentException("axis modulation weight must be in [-1, 1]");
        }
        supportingFragmentIds = List.copyOf(supportingFragmentIds);
    }
}
