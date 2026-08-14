package io.casehub.blocks.negotiation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceptancePolicyTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:01:00Z");

    private NegotiationState threePartyWithProposal() {
        var state = new NegotiationState(List.of(), Set.of("mediator", "a", "b", "c"),
                                         Map.of(), NegotiationOutcome.PENDING);
        return NegotiationFold.propose(state, "p1", "mediator", "Split 50/50", T1);
    }

    private NegotiationState bilateralWithProposal() {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                                         Map.of(), NegotiationOutcome.PENDING);
        return NegotiationFold.propose(state, "p1", "alice", "Price: $100", T1);
    }

    @Nested
    class Unanimous {
        private final AcceptancePolicy policy = new UnanimousAcceptance();

        @Test
        void notAcceptedWhenNoResponses() {
            assertThat(policy.isAccepted(threePartyWithProposal())).isFalse();
        }

        @Test
        void notAcceptedWhenPartialAcceptance() {
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "a", T2);
            s = NegotiationFold.accept(s, "b", T2);
            assertThat(policy.isAccepted(s)).isFalse();
        }

        @Test
        void acceptedWhenAllNonProposerAccept() {
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "a", T2);
            s = NegotiationFold.accept(s, "b", T2);
            s = NegotiationFold.accept(s, "c", T2);
            assertThat(policy.isAccepted(s)).isTrue();
        }

        @Test
        void notAcceptedWhenOneRejects() {
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "a", T2);
            s = NegotiationFold.accept(s, "b", T2);
            s = NegotiationFold.reject(s, "c", "no", T2);
            assertThat(policy.isAccepted(s)).isFalse();
        }

        @Test
        void noActiveProposalReturnsFalse() {
            var empty = new NegotiationState(List.of(), Set.of("a", "b"),
                                             Map.of(), NegotiationOutcome.PENDING);
            assertThat(policy.isAccepted(empty)).isFalse();
        }

        @Test
        void bilateralAcceptedWhenOtherPartyAccepts() {
            var s = bilateralWithProposal();
            s = NegotiationFold.accept(s, "bob", T2);
            assertThat(policy.isAccepted(s)).isTrue();
        }

        @Test
        void proposerExcludedFromQuorum() {
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "mediator", T2);
            assertThat(policy.isAccepted(s)).isFalse();
        }
    }

    @Nested
    class Majority {
        private final AcceptancePolicy policy = new MajorityAcceptance();

        @Test
        void acceptedWhenMajorityAccept() {
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "a", T2);
            s = NegotiationFold.accept(s, "b", T2);
            assertThat(policy.isAccepted(s)).isTrue();
        }

        @Test
        void notAcceptedWithOnlyOneOfThree() {
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "a", T2);
            assertThat(policy.isAccepted(s)).isFalse();
        }

        @Test
        void acceptedWithTwoOfFour() {
            var fourParty = new NegotiationState(List.of(),
                    Set.of("mediator", "a", "b", "c", "d"),
                    Map.of(), NegotiationOutcome.PENDING);
            var s = NegotiationFold.propose(fourParty, "p1", "mediator", "x", T1);
            s = NegotiationFold.accept(s, "a", T2);
            s = NegotiationFold.accept(s, "b", T2);
            assertThat(policy.isAccepted(s)).isFalse();
            s = NegotiationFold.accept(s, "c", T2);
            assertThat(policy.isAccepted(s)).isTrue();
        }

        @Test
        void noActiveProposalReturnsFalse() {
            var empty = new NegotiationState(List.of(), Set.of("a", "b"),
                                             Map.of(), NegotiationOutcome.PENDING);
            assertThat(policy.isAccepted(empty)).isFalse();
        }
    }

    @Nested
    class Threshold {
        @Test
        void acceptedWhenThresholdMet() {
            var policy = new ThresholdAcceptance(2);
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "a", T2);
            s = NegotiationFold.accept(s, "b", T2);
            assertThat(policy.isAccepted(s)).isTrue();
        }

        @Test
        void notAcceptedBelowThreshold() {
            var policy = new ThresholdAcceptance(2);
            var s = threePartyWithProposal();
            s = NegotiationFold.accept(s, "a", T2);
            assertThat(policy.isAccepted(s)).isFalse();
        }

        @Test
        void rejectsZeroThreshold() {
            assertThatThrownBy(() -> new ThresholdAcceptance(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void noActiveProposalReturnsFalse() {
            var policy = new ThresholdAcceptance(1);
            var empty = new NegotiationState(List.of(), Set.of("a", "b"),
                                             Map.of(), NegotiationOutcome.PENDING);
            assertThat(policy.isAccepted(empty)).isFalse();
        }
    }
}
