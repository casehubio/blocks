package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.summarisation.observation.affordance.AffordanceRenderer;
import io.casehub.blocks.summarisation.observation.affordance.CognitiveObservationSections;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class NarrativePromptSection implements PromptSection {

    private final NarrativeOrchestrator narrative;

    public NarrativePromptSection(NarrativeOrchestrator narrative) {
        this.narrative = narrative;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        return narrative.currentNarrative(context.agentId(), context.tenantId())
                .map(state -> {
                    var section = CognitiveObservationSections.narrativeSection(state);
                    return new AffordanceRenderer().renderObservation(List.of(section));
                })
                .filter(s -> !s.isBlank())
                .orElse(null);
    }
}
