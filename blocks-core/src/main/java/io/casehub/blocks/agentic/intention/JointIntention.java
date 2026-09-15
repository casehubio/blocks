package io.casehub.blocks.agentic.intention;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record JointIntention(
        String intentionId,
        String planDescription,
        Set<String> committedParties,
        IntentionStatus status,
        Instant formedAt,
        String dropReason
) {
    public JointIntention {
        Objects.requireNonNull(intentionId);
        Objects.requireNonNull(planDescription);
        committedParties = Set.copyOf(committedParties);
        Objects.requireNonNull(status);
        Objects.requireNonNull(formedAt);
    }

    public static JointIntention form(String intentionId, String planDescription,
                                      Set<String> parties, Instant formedAt) {
        return new JointIntention(intentionId, planDescription, parties,
                IntentionStatus.FORMED, formedAt, null);
    }

    public JointIntention activate() {
        return new JointIntention(intentionId, planDescription, committedParties,
                IntentionStatus.ACTIVE, formedAt, null);
    }

    public JointIntention reconsider() {
        return new JointIntention(intentionId, planDescription, committedParties,
                IntentionStatus.RECONSIDERING, formedAt, null);
    }

    public JointIntention drop(String reason) {
        return new JointIntention(intentionId, planDescription, committedParties,
                IntentionStatus.DROPPED, formedAt, reason);
    }

    public JointIntention fulfill() {
        return new JointIntention(intentionId, planDescription, committedParties,
                IntentionStatus.FULFILLED, formedAt, null);
    }

    public JointIntention withPartyDropped(String party) {
        var remaining = new java.util.LinkedHashSet<>(committedParties);
        remaining.remove(party);
        return new JointIntention(intentionId, planDescription, remaining,
                remaining.isEmpty() ? IntentionStatus.DROPPED : status, formedAt,
                remaining.isEmpty() ? "All parties dropped" : dropReason);
    }
}
