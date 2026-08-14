package io.casehub.blocks.negotiation;

import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminationConditionTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:01:00Z");

    private TerminationContext<NegotiationState> ctx(NegotiationState state) {
        return new TerminationContext<>(state, state.round(), Duration.ZERO, List.of());
    }

    private NegotiationState stateWithRounds(int rounds) {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        for (int i = 0; i < rounds; i++) {
            state = NegotiationFold.propose(state, "p" + i, i % 2 == 0 ? "alice" : "bob",
                    "round " + (i + 1), T1.plusSeconds(i * 60));
        }
        return state;
    }

    @Nested
    class MaxRounds {
        @Test
        void continuesBeforeMax() {
            var tc = new MaxRoundsTermination(3);
            assertThat(tc.evaluate(ctx(stateWithRounds(2)))).isInstanceOf(TerminationDecision.Continue.class);
        }

        @Test
        void completesAtMax() {
            var tc = new MaxRoundsTermination(3);
            assertThat(tc.evaluate(ctx(stateWithRounds(3)))).isInstanceOf(TerminationDecision.Complete.class);
        }

        @Test
        void completesAboveMax() {
            var tc = new MaxRoundsTermination(2);
            assertThat(tc.evaluate(ctx(stateWithRounds(5)))).isInstanceOf(TerminationDecision.Complete.class);
        }

        @Test
        void rejectsZero() {
            assertThatThrownBy(() -> new MaxRoundsTermination(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Accepted {
        @Test
        void continuesWhenPending() {
            var tc = new AcceptedTermination();
            var state = stateWithRounds(1);
            assertThat(tc.evaluate(ctx(state))).isInstanceOf(TerminationDecision.Continue.class);
        }

        @Test
        void completesWhenAgreed() {
            var tc = new AcceptedTermination();
            var state = stateWithRounds(1);
            state = NegotiationFold.accept(state, "bob", T2);
            state = NegotiationFold.agree(state);
            assertThat(tc.evaluate(ctx(state))).isInstanceOf(TerminationDecision.Complete.class);
        }

        @Test
        void continuesWhenDeadlocked() {
            var tc = new AcceptedTermination();
            var state = stateWithRounds(1);
            state = NegotiationFold.deadlock(state);
            assertThat(tc.evaluate(ctx(state))).isInstanceOf(TerminationDecision.Continue.class);
        }
    }

    @Nested
    class TerminalOutcome {
        @Test
        void continuesWhenPending() {
            var tc = new TerminalOutcomeTermination();
            assertThat(tc.evaluate(ctx(stateWithRounds(1)))).isInstanceOf(TerminationDecision.Continue.class);
        }

        @Test
        void completesWhenAgreed() {
            var tc = new TerminalOutcomeTermination();
            var state = stateWithRounds(1);
            state = NegotiationFold.accept(state, "bob", T2);
            state = NegotiationFold.agree(state);
            assertThat(tc.evaluate(ctx(state))).isInstanceOf(TerminationDecision.Complete.class);
        }

        @Test
        void failsWhenDeadlocked() {
            var tc = new TerminalOutcomeTermination();
            var state = stateWithRounds(1);
            state = NegotiationFold.deadlock(state);
            assertThat(tc.evaluate(ctx(state))).isInstanceOf(TerminationDecision.Failed.class);
        }

        @Test
        void failsWhenWithdrawn() {
            var tc = new TerminalOutcomeTermination();
            var state = stateWithRounds(1);
            state = NegotiationFold.withdraw(state, "alice", "quit", T2);
            assertThat(tc.evaluate(ctx(state))).isInstanceOf(TerminationDecision.Failed.class);
        }
    }

    @Nested
    class Deadline {
        @Test
        void continuesBeforeDeadline() {
            var tc = new DeadlineTermination(Instant.parse("2026-06-01T00:00:00Z"));
            assertThat(tc.evaluate(ctx(stateWithRounds(1)))).isInstanceOf(TerminationDecision.Continue.class);
        }

        @Test
        void completesAfterDeadline() {
            var tc = new DeadlineTermination(Instant.parse("2025-06-01T00:00:00Z"));
            assertThat(tc.evaluate(ctx(stateWithRounds(1)))).isInstanceOf(TerminationDecision.Complete.class);
        }

        @Test
        void continuesWithNoProposals() {
            var tc = new DeadlineTermination(Instant.parse("2025-06-01T00:00:00Z"));
            var empty = new NegotiationState(List.of(), Set.of("a", "b"),
                    Map.of(), NegotiationOutcome.PENDING);
            assertThat(tc.evaluate(ctx(empty))).isInstanceOf(TerminationDecision.Continue.class);
        }
    }

    @Nested
    class Composite {
        @Test
        void firstNonContinueWins() {
            var composite = new NegotiationCompositeTermination(List.of(
                    new MaxRoundsTermination(10),
                    new AcceptedTermination()
            ));
            var state = stateWithRounds(1);
            state = NegotiationFold.accept(state, "bob", T2);
            state = NegotiationFold.agree(state);
            var decision = composite.evaluate(ctx(state));
            assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
            assertThat(((TerminationDecision.Complete) decision).result()).isEqualTo("Proposal accepted");
        }

        @Test
        void continuesWhenAllContinue() {
            var composite = new NegotiationCompositeTermination(List.of(
                    new MaxRoundsTermination(10),
                    new AcceptedTermination()
            ));
            assertThat(composite.evaluate(ctx(stateWithRounds(1)))).isInstanceOf(TerminationDecision.Continue.class);
        }
    }
}
