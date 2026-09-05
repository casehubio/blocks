package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.summarisation.observation.affordance.AffordanceRenderer;
import io.casehub.blocks.summarisation.observation.affordance.CognitiveObservationSections;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DrivePromptSection implements PromptSection {

    private final DriveOrchestrator drives;

    public DrivePromptSection(DriveOrchestrator drives) {
        this.drives = drives;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        return drives.currentDrives(context.agentId(), context.tenantId())
                .map(profile -> {
                    var section = CognitiveObservationSections.motivationalStateSection(profile);
                    return new AffordanceRenderer().renderObservation(List.of(section));
                })
                .filter(s -> !s.isBlank())
                .orElse(null);
    }
}
