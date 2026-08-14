# Iterative Auction Protocol — Design Spec

**Issue:** casehubio/blocks#108
**Branch:** issue-105-pattern-gaps
**Date:** 2026-08-14

## Summary

`AuctionAggregation` — an `AggregationStrategy<AuctionState>` implementation in `io.casehub.blocks.agentic.aggregation` for multi-round iterative bidding. Supports English (ascending) and Dutch (descending) auction types. Composes with the existing LOOP execution pattern for multi-round iteration.

## Architecture

### Package placement

Extends `io.casehub.blocks.agentic.aggregation` — the existing home for `AggregationStrategy` implementations (alongside `MajorityVote`, `CollectAll`, `PassThrough`).

### How it works

The LOOP pattern drives iteration. Each round, agents submit bids as `AgentResult` outputs. `AuctionAggregation` parses bids, updates `AuctionState`, and returns:
- `Partial(updatedState, agentCount)` — new valid bids received, continue
- `Resolved(AuctionOutcome)` — auction complete (winner found or no-sale)
- `Deadlocked(reason)` — cannot continue (e.g., all bidders passed on first round)

### English auction flow

1. Agents submit bids above current price
2. Highest new bid becomes the leader
3. If no new bids above current leader → auction closes, leader wins
4. If highest bid < reserve price → no sale

### Dutch auction flow

1. Price starts high and is set per-round by the orchestrator (via prompt)
2. Agents accept (bid = current price) or pass
3. First acceptance → auction closes, acceptor wins
4. If price hits floor with no acceptance → no sale

## Types

### AuctionType

```java
public enum AuctionType { ENGLISH, DUTCH }
```

### Bid

```java
public record Bid(String bidder, double amount, int round, Instant submittedAt) {}
```

### AuctionState

```java
public record AuctionState(
    AuctionType type,
    double reservePrice,
    List<Bid> bidHistory,
    @Nullable Bid currentHighBid,
    int currentRound
) {}
```

### AuctionOutcome

```java
public record AuctionOutcome(
    @Nullable Bid winningBid,
    List<Bid> allBids,
    boolean sold
) {}
```

### BidExtractor

```java
@FunctionalInterface
public interface BidExtractor {
    @Nullable Bid extract(AgentResult result, int round);
}
```

Consumer provides the extraction logic — how to parse a bid amount from an agent's output.

### AuctionAggregation

```java
public class AuctionAggregation implements AggregationStrategy<AuctionState> {
    // Constructor: BidExtractor, AuctionType, reservePrice
    // aggregate(): parses bids, applies auction rules, returns result
}
```

## File Inventory

6 production files, 1 test file. All in `io.casehub.blocks.agentic.aggregation`.

## Scope Exclusions

- Sealed-bid auctions — already compositional via existing routing strategies
- Combinatorial auctions — out of scope (no use case)
- Bid validation beyond price ordering — consumer concern
