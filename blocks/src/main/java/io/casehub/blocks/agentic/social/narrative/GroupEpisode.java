package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record GroupEpisode(
        String id,
        Instant from,
        @Nullable Instant to,
        List<String> thematicTags,
        String description,
        double emotionalValence,
        Set<String> membershipAtTime,
        Map<String, String> roleAttributions,
        double consensusLevel
) implements NarrativeFragment {
    public GroupEpisode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(description);
        if (emotionalValence < -1.0 || emotionalValence > 1.0)
            throw new IllegalArgumentException("emotionalValence must be in [-1, 1]");
        membershipAtTime = Set.copyOf(membershipAtTime);
        roleAttributions = Map.copyOf(roleAttributions);
        if (consensusLevel < 0.0 || consensusLevel > 1.0)
            throw new IllegalArgumentException("consensusLevel must be in [0, 1]");
    }
}
