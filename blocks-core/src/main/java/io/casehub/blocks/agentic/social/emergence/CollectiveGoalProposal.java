package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;

import java.util.Objects;
import java.util.Set;

public record CollectiveGoalProposal(
        DriveAlignment alignment,
        String goalDescription,
        Set<String> proposedParticipants,
        DriveAxis primaryAxis) {
    public CollectiveGoalProposal {
        Objects.requireNonNull(alignment);
        Objects.requireNonNull(goalDescription);
        proposedParticipants = Set.copyOf(proposedParticipants);
        Objects.requireNonNull(primaryAxis);
    }
}
