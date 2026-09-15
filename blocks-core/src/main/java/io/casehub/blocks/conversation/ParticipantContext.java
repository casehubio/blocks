package io.casehub.blocks.conversation;

import java.util.Set;

public record ParticipantContext(
        Set<String> allParticipants,
        Set<String> respondedBy,
        Set<String> acknowledgedBy,
        Set<String> completedBy,
        Set<String> disputedBy,
        Set<String> failedBy,
        int roundsSinceLastActivity) {
    public ParticipantContext {
        allParticipants = Set.copyOf(allParticipants);
        respondedBy = Set.copyOf(respondedBy);
        acknowledgedBy = Set.copyOf(acknowledgedBy);
        completedBy = Set.copyOf(completedBy);
        disputedBy = Set.copyOf(disputedBy);
        failedBy = Set.copyOf(failedBy);
    }
}
