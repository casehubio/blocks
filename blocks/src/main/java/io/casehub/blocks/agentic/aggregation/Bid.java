package io.casehub.blocks.agentic.aggregation;

import java.time.Instant;
import java.util.Objects;

public record Bid(String bidder, double amount, int round, Instant submittedAt) {
    public Bid {
        Objects.requireNonNull(bidder);
        Objects.requireNonNull(submittedAt);
        if (amount < 0) throw new IllegalArgumentException("Bid amount must be >= 0");
        if (round < 1) throw new IllegalArgumentException("Round must be >= 1");
    }
}
