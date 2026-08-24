package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public sealed interface NarrativeFragment
        permits IndividualEpisode, GroupEpisode, DerivedTheme {

    String id();

    Instant from();

    @Nullable Instant to();

    List<String> thematicTags();
}
