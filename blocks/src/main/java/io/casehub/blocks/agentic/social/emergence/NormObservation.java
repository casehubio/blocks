package io.casehub.blocks.agentic.social.emergence;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record NormObservation(
        String observationId,
        String tenantId,
        String behavioralPattern,
        Set<String> involvedAgents,
        String conversationId,
        Instant observedAt,
        boolean patternFollowed) {
    public NormObservation {
        Objects.requireNonNull(observationId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(behavioralPattern);
        involvedAgents = Set.copyOf(involvedAgents);
        Objects.requireNonNull(observedAt);
    }
}
