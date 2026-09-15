package io.casehub.blocks.negotiation;

import java.time.Instant;
import java.util.Objects;

public record Proposal(
        String proposalId,
        String proposer,
        String content,
        int round,
        Instant createdAt,
        ProposalStatus status
) {
    public Proposal {
        Objects.requireNonNull(proposalId);
        Objects.requireNonNull(proposer);
        Objects.requireNonNull(content);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(status);
        if (round < 1) throw new IllegalArgumentException("round must be >= 1");
    }
}
