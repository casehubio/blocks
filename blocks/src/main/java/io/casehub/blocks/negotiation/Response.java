package io.casehub.blocks.negotiation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record Response(
        String party,
        PartyDecision decision,
        @Nullable String reason,
        Instant respondedAt
) {
    public Response {
        Objects.requireNonNull(party);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(respondedAt);
    }
}
