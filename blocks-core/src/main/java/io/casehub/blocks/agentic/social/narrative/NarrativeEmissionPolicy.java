package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.TokenJaccardDistance;
import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.summarisation.EmissionPolicy;
import io.casehub.blocks.summarisation.LevelEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class NarrativeEmissionPolicy
        implements EmissionPolicy<ReflectionEntry, NarrativeState> {

    private final NarrativeSynthesisGate gate;

    public NarrativeEmissionPolicy(NarrativeSynthesisGate gate) {
        this.gate = gate;
    }

    @Override
    public boolean shouldEmit(List<LevelEvent<ReflectionEntry>> buffered,
                              @Nullable NarrativeState currentState,
                              long now) {
        if (buffered.isEmpty()) return false;

        if (currentState != null) {
            long sinceSynthesis = now - currentState.synthesisedAt()
                    .toEpochMilli();
            if (sinceSynthesis >= gate.quietPeriodBypass().toMillis()) {
                return true;
            }
        } else {
            return true;
        }

        if (buffered.size() < gate.minNewReflections()) {
            return false;
        }

        var reflectionText = buffered.stream()
                .map(e -> e.payload().insight())
                .collect(Collectors.joining("\n"));
        var narrativeText = currentState.episodes().stream()
                .map(IndividualEpisode::description)
                .collect(Collectors.joining("\n"));
        double novelty = TokenJaccardDistance.distance(
                reflectionText, narrativeText);
        return novelty >= gate.noveltyThreshold();
    }
}
