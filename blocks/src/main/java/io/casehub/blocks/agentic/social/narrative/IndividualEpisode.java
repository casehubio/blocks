package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record IndividualEpisode(
        String id,
        Instant from,
        @Nullable Instant to,
        List<String> thematicTags,
        String description,
        double emotionalValence,
        List<String> sourceReflectionIds
) implements NarrativeFragment {
    public IndividualEpisode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(description);
        if (emotionalValence < -1.0 || emotionalValence > 1.0)
            throw new IllegalArgumentException("emotionalValence must be in [-1, 1]");
        sourceReflectionIds = List.copyOf(sourceReflectionIds);
    }
}
