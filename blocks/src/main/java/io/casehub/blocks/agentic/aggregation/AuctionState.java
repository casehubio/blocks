package io.casehub.blocks.agentic.aggregation;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record AuctionState(
        AuctionType type,
        double reservePrice,
        List<Bid> bidHistory,
        @Nullable Bid currentHighBid,
        int currentRound
) {
    public AuctionState {
        Objects.requireNonNull(type);
        bidHistory = List.copyOf(bidHistory);
        if (reservePrice < 0) throw new IllegalArgumentException("Reserve price must be >= 0");
    }

    public static AuctionState initial(AuctionType type, double reservePrice) {
        return new AuctionState(type, reservePrice, List.of(), null, 0);
    }
}
