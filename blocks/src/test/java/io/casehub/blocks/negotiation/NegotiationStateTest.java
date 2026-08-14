package io.casehub.blocks.negotiation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NegotiationStateTest {

    @Nested
    class Enums {
        @Test
        void outcomeTerminalStates() {
            assertThat(NegotiationOutcome.PENDING.isTerminal()).isFalse();
            assertThat(NegotiationOutcome.AGREED.isTerminal()).isTrue();
            assertThat(NegotiationOutcome.DEADLOCKED.isTerminal()).isTrue();
            assertThat(NegotiationOutcome.WITHDRAWN.isTerminal()).isTrue();
        }

        @Test
        void proposalStatusTerminalStates() {
            assertThat(ProposalStatus.ACTIVE.isTerminal()).isFalse();
            assertThat(ProposalStatus.SUPERSEDED.isTerminal()).isFalse();
            assertThat(ProposalStatus.ACCEPTED.isTerminal()).isTrue();
            assertThat(ProposalStatus.REJECTED.isTerminal()).isTrue();
        }
    }

    @Nested
    class ProposalRecord {
        @Test
        void validProposal() {
            var p = new Proposal("p1", "alice", "Price: $100", 1,
                                 Instant.now(), ProposalStatus.ACTIVE);
            assertThat(p.proposalId()).isEqualTo("p1");
            assertThat(p.proposer()).isEqualTo("alice");
            assertThat(p.round()).isEqualTo(1);
        }

        @Test
        void rejectsZeroRound() {
            assertThatThrownBy(() -> new Proposal("p1", "alice", "x", 0,
                                                   Instant.now(), ProposalStatus.ACTIVE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullFields() {
            assertThatThrownBy(() -> new Proposal(null, "alice", "x", 1,
                                                   Instant.now(), ProposalStatus.ACTIVE))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ResponseRecord {
        @Test
        void validResponse() {
            var r = new Response("bob", PartyDecision.ACCEPTED, null, Instant.now());
            assertThat(r.party()).isEqualTo("bob");
            assertThat(r.decision()).isEqualTo(PartyDecision.ACCEPTED);
            assertThat(r.reason()).isNull();
        }

        @Test
        void responseWithReason() {
            var r = new Response("bob", PartyDecision.REJECTED, "too expensive", Instant.now());
            assertThat(r.reason()).isEqualTo("too expensive");
        }
    }

    @Nested
    class StateRecord {
        @Test
        void emptyState() {
            var state = new NegotiationState(List.of(), Set.of("a", "b"),
                                             Map.of(), NegotiationOutcome.PENDING);
            assertThat(state.activeProposal()).isNull();
            assertThat(state.round()).isEqualTo(0);
            assertThat(state.hasActiveProposal()).isFalse();
            assertThat(state.parties()).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        void activeProposalReturnsLatestActive() {
            var p1 = new Proposal("p1", "a", "x", 1, Instant.now(), ProposalStatus.SUPERSEDED);
            var p2 = new Proposal("p2", "b", "y", 2, Instant.now(), ProposalStatus.ACTIVE);
            var state = new NegotiationState(List.of(p1, p2), Set.of("a", "b"),
                                             Map.of(), NegotiationOutcome.PENDING);
            assertThat(state.activeProposal()).isEqualTo(p2);
            assertThat(state.round()).isEqualTo(2);
            assertThat(state.hasActiveProposal()).isTrue();
        }

        @Test
        void noActiveProposalWhenAllSuperseded() {
            var p1 = new Proposal("p1", "a", "x", 1, Instant.now(), ProposalStatus.SUPERSEDED);
            var p2 = new Proposal("p2", "b", "y", 2, Instant.now(), ProposalStatus.ACCEPTED);
            var state = new NegotiationState(List.of(p1, p2), Set.of("a", "b"),
                                             Map.of(), NegotiationOutcome.AGREED);
            assertThat(state.activeProposal()).isNull();
        }

        @Test
        void defensiveCopies() {
            var proposals = new java.util.ArrayList<>(List.of(
                    new Proposal("p1", "a", "x", 1, Instant.now(), ProposalStatus.ACTIVE)));
            var state = new NegotiationState(proposals, Set.of("a"), Map.of(),
                                             NegotiationOutcome.PENDING);
            proposals.clear();
            assertThat(state.proposals()).hasSize(1);
        }
    }
}
