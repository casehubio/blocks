package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.eidos.api.AgentDescriptor;

import java.util.Objects;

public record GoalEscalationContext(
        NarrativeState narrative,
        DriveProfile drives,
        AgentDescriptor descriptor) {
    public GoalEscalationContext {
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(drives);
        Objects.requireNonNull(descriptor);
    }
}
