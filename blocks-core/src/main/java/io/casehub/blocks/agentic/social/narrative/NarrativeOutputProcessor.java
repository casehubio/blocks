package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.summarisation.OutputProcessor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NarrativeOutputProcessor
        implements OutputProcessor<NarrativeState, NarrativeState> {

    private final NarrativeConfig config;

    public NarrativeOutputProcessor(NarrativeConfig config) {
        this.config = config;
    }

    @Override
    public List<NarrativeState> process(
            List<NarrativeState> outputs,
            @Nullable NarrativeState currentState) {
        return outputs.stream()
                .map(this::prune)
                .toList();
    }

    private NarrativeState prune(NarrativeState state) {
        var episodes = new ArrayList<>(state.episodes());
        pruneEpisodes(episodes);

        var themes = new ArrayList<>(state.themes());
        pruneThemes(themes);

        var allFragments = new ArrayList<NarrativeFragment>();
        allFragments.addAll(episodes);
        allFragments.addAll(state.groupEpisodes());
        allFragments.addAll(themes);

        return new NarrativeState(state.scopeId(), state.tenantId(),
                state.scope(), allFragments, state.synthesisedAt(),
                state.reflectionCountAtSynthesis());
    }

    private void pruneEpisodes(List<IndividualEpisode> episodes) {
        if (episodes.size() > config.maxEpisodes()) {
            episodes.sort(Comparator.comparing(IndividualEpisode::from));
            while (episodes.size() > config.maxEpisodes()) {
                episodes.removeFirst();
            }
        }
    }

    private void pruneThemes(List<DerivedTheme> themes) {
        themes.removeIf(t -> t.salience() < config.themeSalienceFloor());
        if (themes.size() > config.maxThemes()) {
            themes.sort(Comparator.comparingDouble(DerivedTheme::salience));
            while (themes.size() > config.maxThemes()) {
                themes.removeFirst();
            }
        }
    }
}
