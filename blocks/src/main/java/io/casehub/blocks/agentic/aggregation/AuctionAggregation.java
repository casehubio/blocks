package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AuctionAggregation implements AggregationStrategy<AuctionState> {

    private final BidExtractor extractor;

    public AuctionAggregation(BidExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor);
    }

    @Override
    public AggregationResult aggregate(List<AgentResult> results,
                                       AggregationContext<AuctionState> context) {
        AuctionState state = context.state();
        int round = state.currentRound() + 1;

        List<Bid> roundBids = new ArrayList<>();
        for (var result : results) {
            Bid bid = extractor.extract(result, round);
            if (bid != null) roundBids.add(bid);
        }

        return switch (state.type()) {
            case ENGLISH -> aggregateEnglish(state, roundBids, round);
            case DUTCH -> aggregateDutch(state, roundBids, round);
        };
    }

    private AggregationResult aggregateEnglish(AuctionState state, List<Bid> roundBids, int round) {
        var allBids = new ArrayList<>(state.bidHistory());
        allBids.addAll(roundBids);

        Bid currentHigh = state.currentHighBid();
        List<Bid> validBids = roundBids.stream()
                .filter(b -> currentHigh == null || b.amount() > currentHigh.amount())
                .filter(b -> b.amount() >= state.reservePrice())
                .sorted(Comparator.comparingDouble(Bid::amount).reversed())
                .toList();

        if (validBids.isEmpty()) {
            if (currentHigh != null && currentHigh.amount() >= state.reservePrice()) {
                return new AggregationResult.Resolved(
                        new AuctionOutcome(currentHigh, allBids, true));
            }
            if (round == 1 && roundBids.isEmpty()) {
                return new AggregationResult.Deadlocked("No bids received in first round");
            }
            return new AggregationResult.Resolved(
                    new AuctionOutcome(null, allBids, false));
        }

        Bid newHigh = validBids.getFirst();
        var nextState = new AuctionState(state.type(), state.reservePrice(),
                allBids, newHigh, round);
        return new AggregationResult.Partial(nextState, roundBids.size());
    }

    private AggregationResult aggregateDutch(AuctionState state, List<Bid> roundBids, int round) {
        var allBids = new ArrayList<>(state.bidHistory());
        allBids.addAll(roundBids);

        if (!roundBids.isEmpty()) {
            Bid firstAcceptor = roundBids.stream()
                    .min(Comparator.comparing(Bid::submittedAt))
                    .orElseThrow();
            return new AggregationResult.Resolved(
                    new AuctionOutcome(firstAcceptor, allBids, true));
        }

        var nextState = new AuctionState(state.type(), state.reservePrice(),
                allBids, null, round);
        return new AggregationResult.Partial(nextState, 0);
    }
}
