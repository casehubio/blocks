package io.casehub.blocks.conversation;

import java.util.Set;

public record GroundedFact(
        String pointId,
        String topic,
        EpistemicStatus status,
        String content,
        Set<String> acknowledgedBy,
        Set<String> disputedBy,
        int round) {
    public GroundedFact {
        acknowledgedBy = Set.copyOf(acknowledgedBy);
        disputedBy = Set.copyOf(disputedBy);
    }
}
