package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record NarrativeState(
        String scopeId,
        String tenantId,
        NarrativeScope scope,
        List<NarrativeFragment> fragments,
        Instant synthesisedAt,
        int reflectionCountAtSynthesis
) {
    public NarrativeState {
        Objects.requireNonNull(scopeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(scope);
        fragments = List.copyOf(fragments);
        Objects.requireNonNull(synthesisedAt);
    }

    public List<IndividualEpisode> episodes() {
        return fragments.stream()
                .filter(IndividualEpisode.class::isInstance)
                .map(IndividualEpisode.class::cast)
                .toList();
    }

    public List<GroupEpisode> groupEpisodes() {
        return fragments.stream()
                .filter(GroupEpisode.class::isInstance)
                .map(GroupEpisode.class::cast)
                .toList();
    }

    public List<DerivedTheme> themes() {
        return fragments.stream()
                .filter(DerivedTheme.class::isInstance)
                .map(DerivedTheme.class::cast)
                .toList();
    }

    public @Nullable DerivedTheme dominantTheme() {
        return themes().stream()
                .max(Comparator.comparingDouble(DerivedTheme::salience))
                .orElse(null);
    }
}
