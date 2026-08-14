package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionAggregationTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");

    private final BidExtractor extractor = (result, round) -> {
        if (result.output() instanceof Double amount) {
            return new Bid(result.agent().name(), amount, round, T1);
        }
        return null;
    };

    private AgentResult agentResult(String name, Object output) {
        return AgentResult.success(new AgentRef.ExternalAgent(name, null), output);
    }

    @Nested
    class EnglishAuction {
        private final AuctionAggregation aggregation = new AuctionAggregation(extractor);

        @Test
        void firstRoundWithBidsReturnsPartial() {
            var state = AuctionState.initial(AuctionType.ENGLISH, 50.0);
            var results = List.of(agentResult("a", 100.0), agentResult("b", 120.0));
            var result = aggregation.aggregate(results, new AggregationContext<>(state));
            assertThat(result).isInstanceOf(AggregationResult.Partial.class);
            var partial = (AggregationResult.Partial) result;
            var nextState = (AuctionState) partial.collected();
            assertThat(nextState.currentHighBid().amount()).isEqualTo(120.0);
            assertThat(nextState.currentHighBid().bidder()).isEqualTo("b");
            assertThat(nextState.currentRound()).isEqualTo(1);
        }

        @Test
        void noBidsAboveCurrentHighResolves() {
            var state = new AuctionState(AuctionType.ENGLISH, 50.0,
                    List.of(new Bid("b", 120.0, 1, T1)),
                    new Bid("b", 120.0, 1, T1), 1);
            var results = List.of(agentResult("a", 100.0));
            var result = aggregation.aggregate(results, new AggregationContext<>(state));
            assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
            var outcome = (AuctionOutcome) ((AggregationResult.Resolved) result).value();
            assertThat(outcome.sold()).isTrue();
            assertThat(outcome.winningBid().bidder()).isEqualTo("b");
            assertThat(outcome.winningBid().amount()).isEqualTo(120.0);
        }

        @Test
        void noBidsOnFirstRoundDeadlocks() {
            var state = AuctionState.initial(AuctionType.ENGLISH, 50.0);
            var results = List.of(agentResult("a", "pass"), agentResult("b", "pass"));
            var result = aggregation.aggregate(results, new AggregationContext<>(state));
            assertThat(result).isInstanceOf(AggregationResult.Deadlocked.class);
        }

        @Test
        void bidsBelowReserveAreIgnored() {
            var state = AuctionState.initial(AuctionType.ENGLISH, 200.0);
            var results = List.of(agentResult("a", 100.0), agentResult("b", 150.0));
            var result = aggregation.aggregate(results, new AggregationContext<>(state));
            assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
            var outcome = (AuctionOutcome) ((AggregationResult.Resolved) result).value();
            assertThat(outcome.sold()).isFalse();
        }

        @Test
        void multiRoundAuction() {
            var state = AuctionState.initial(AuctionType.ENGLISH, 50.0);

            var r1 = aggregation.aggregate(
                    List.of(agentResult("a", 100.0), agentResult("b", 120.0)),
                    new AggregationContext<>(state));
            assertThat(r1).isInstanceOf(AggregationResult.Partial.class);
            var s1 = (AuctionState) ((AggregationResult.Partial) r1).collected();

            var r2 = aggregation.aggregate(
                    List.of(agentResult("a", 150.0), agentResult("b", "pass")),
                    new AggregationContext<>(s1));
            assertThat(r2).isInstanceOf(AggregationResult.Partial.class);
            var s2 = (AuctionState) ((AggregationResult.Partial) r2).collected();
            assertThat(s2.currentHighBid().bidder()).isEqualTo("a");

            var r3 = aggregation.aggregate(
                    List.of(agentResult("a", "pass"), agentResult("b", "pass")),
                    new AggregationContext<>(s2));
            assertThat(r3).isInstanceOf(AggregationResult.Resolved.class);
            var outcome = (AuctionOutcome) ((AggregationResult.Resolved) r3).value();
            assertThat(outcome.sold()).isTrue();
            assertThat(outcome.winningBid().amount()).isEqualTo(150.0);
        }
    }

    @Nested
    class DutchAuction {
        private final AuctionAggregation aggregation = new AuctionAggregation(extractor);

        @Test
        void firstAcceptorWins() {
            var state = AuctionState.initial(AuctionType.DUTCH, 0.0);
            var results = List.of(agentResult("a", 500.0));
            var result = aggregation.aggregate(results, new AggregationContext<>(state));
            assertThat(result).isInstanceOf(AggregationResult.Resolved.class);
            var outcome = (AuctionOutcome) ((AggregationResult.Resolved) result).value();
            assertThat(outcome.sold()).isTrue();
            assertThat(outcome.winningBid().bidder()).isEqualTo("a");
        }

        @Test
        void noAcceptanceContinues() {
            var state = AuctionState.initial(AuctionType.DUTCH, 0.0);
            var results = List.of(agentResult("a", "pass"), agentResult("b", "pass"));
            var result = aggregation.aggregate(results, new AggregationContext<>(state));
            assertThat(result).isInstanceOf(AggregationResult.Partial.class);
        }
    }
}
