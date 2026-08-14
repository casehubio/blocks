package io.casehub.blocks.negotiation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationFoldTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:01:00Z");
    private static final Instant T3 = Instant.parse("2026-01-01T00:02:00Z");

    private final NegotiationState empty = new NegotiationState(
            List.of(), Set.of("alice", "bob"), Map.of(), NegotiationOutcome.PENDING);

    @Nested
    class Propose {
        @Test
        void initialProposal() {
            var result = NegotiationFold.propose(empty, "p1", "alice", "Price: $100", T1);
            assertThat(result.proposals()).hasSize(1);
            assertThat(result.activeProposal().proposalId()).isEqualTo("p1");
            assertThat(result.activeProposal().proposer()).isEqualTo("alice");
            assertThat(result.activeProposal().round()).isEqualTo(1);
            assertThat(result.activeProposal().status()).isEqualTo(ProposalStatus.ACTIVE);
            assertThat(result.responses()).isEmpty();
            assertThat(result.outcome()).isEqualTo(NegotiationOutcome.PENDING);
        }

        @Test
        void counterProposalSupersedesPrevious() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.propose(s1, "p2", "bob", "$80", T2);
            assertThat(s2.proposals()).hasSize(2);
            assertThat(s2.proposals().get(0).status()).isEqualTo(ProposalStatus.SUPERSEDED);
            assertThat(s2.activeProposal().proposalId()).isEqualTo("p2");
            assertThat(s2.activeProposal().round()).isEqualTo(2);
            assertThat(s2.responses()).isEmpty();
        }

        @Test
        void counterProposalClearsResponses() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.reject(s1, "bob", "too much", T2);
            assertThat(s2.responses()).hasSize(1);
            var s3 = NegotiationFold.propose(s2, "p2", "bob", "$80", T3);
            assertThat(s3.responses()).isEmpty();
        }

        @Test
        void proposerAddedToParties() {
            var minimal = new NegotiationState(List.of(), Set.of(), Map.of(),
                                               NegotiationOutcome.PENDING);
            var result = NegotiationFold.propose(minimal, "p1", "charlie", "$50", T1);
            assertThat(result.parties()).contains("charlie");
        }

        @Test
        void preservesExistingParties() {
            var result = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            assertThat(result.parties()).containsAll(Set.of("alice", "bob"));
        }
    }

    @Nested
    class Accept {
        @Test
        void recordsAcceptance() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.accept(s1, "bob", T2);
            assertThat(s2.responses()).containsKey("bob");
            assertThat(s2.responses().get("bob").decision()).isEqualTo(PartyDecision.ACCEPTED);
            assertThat(s2.responses().get("bob").reason()).isNull();
        }

        @Test
        void noActiveProposalReturnsUnchanged() {
            var result = NegotiationFold.accept(empty, "bob", T1);
            assertThat(result).isEqualTo(empty);
        }

        @Test
        void responderAddedToParties() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.accept(s1, "charlie", T2);
            assertThat(s2.parties()).contains("charlie");
        }

        @Test
        void doesNotChangeOutcome() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.accept(s1, "bob", T2);
            assertThat(s2.outcome()).isEqualTo(NegotiationOutcome.PENDING);
        }
    }

    @Nested
    class Reject {
        @Test
        void recordsRejectionWithReason() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.reject(s1, "bob", "too expensive", T2);
            assertThat(s2.responses()).containsKey("bob");
            var resp = s2.responses().get("bob");
            assertThat(resp.decision()).isEqualTo(PartyDecision.REJECTED);
            assertThat(resp.reason()).isEqualTo("too expensive");
        }

        @Test
        void noActiveProposalReturnsUnchanged() {
            var result = NegotiationFold.reject(empty, "bob", "no", T1);
            assertThat(result).isEqualTo(empty);
        }
    }

    @Nested
    class Agree {
        @Test
        void marksProposalAcceptedAndOutcomeAgreed() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.accept(s1, "bob", T2);
            var s3 = NegotiationFold.agree(s2);
            assertThat(s3.outcome()).isEqualTo(NegotiationOutcome.AGREED);
            assertThat(s3.proposals().get(0).status()).isEqualTo(ProposalStatus.ACCEPTED);
        }

        @Test
        void noActiveProposalReturnsUnchanged() {
            var result = NegotiationFold.agree(empty);
            assertThat(result).isEqualTo(empty);
        }
    }

    @Nested
    class Deadlock {
        @Test
        void marksProposalRejectedAndOutcomeDeadlocked() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.deadlock(s1);
            assertThat(s2.outcome()).isEqualTo(NegotiationOutcome.DEADLOCKED);
            assertThat(s2.proposals().get(0).status()).isEqualTo(ProposalStatus.REJECTED);
        }
    }

    @Nested
    class Withdraw {
        @Test
        void marksOutcomeWithdrawnAndProposalRejected() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.withdraw(s1, "alice", "changed mind", T2);
            assertThat(s2.outcome()).isEqualTo(NegotiationOutcome.WITHDRAWN);
            assertThat(s2.proposals().get(0).status()).isEqualTo(ProposalStatus.REJECTED);
            assertThat(s2.responses()).containsKey("alice");
            assertThat(s2.responses().get("alice").decision()).isEqualTo(PartyDecision.REJECTED);
        }

        @Test
        void withdrawalReasonRecorded() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.withdraw(s1, "alice", "changed mind", T2);
            assertThat(s2.responses().get("alice").reason()).isEqualTo("changed mind");
        }
    }

    @Nested
    class MultiRound {
        @Test
        void threeRoundNegotiation() {
            var s1 = NegotiationFold.propose(empty, "p1", "alice", "$100", T1);
            var s2 = NegotiationFold.reject(s1, "bob", "too high", T2);
            var s3 = NegotiationFold.propose(s2, "p2", "bob", "$80", T2);
            var s4 = NegotiationFold.propose(s3, "p3", "alice", "$90", T3);
            var s5 = NegotiationFold.accept(s4, "bob", T3);
            var s6 = NegotiationFold.agree(s5);

            assertThat(s6.proposals()).hasSize(3);
            assertThat(s6.proposals().get(0).status()).isEqualTo(ProposalStatus.SUPERSEDED);
            assertThat(s6.proposals().get(1).status()).isEqualTo(ProposalStatus.SUPERSEDED);
            assertThat(s6.proposals().get(2).status()).isEqualTo(ProposalStatus.ACCEPTED);
            assertThat(s6.outcome()).isEqualTo(NegotiationOutcome.AGREED);
        }
    }
}
