package io.casehub.blocks.negotiation;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationProjectionTest {

    private static final UUID CHANNEL = UUID.randomUUID();
    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:01:00Z");
    private static final Instant T3 = Instant.parse("2026-01-01T00:02:00Z");

    private final NegotiationProjection projection = new NegotiationProjection(
            Set.of("alice", "bob"), new UnanimousAcceptance());

    private MessageView msg(MessageType type, String sender, String content,
                            String correlationId, Instant createdAt) {
        return new MessageView(1L, CHANNEL, sender, type, content, null, correlationId,
                null, null, null, List.of(), ActorType.AGENT, createdAt, null, 0);
    }

    @Nested
    class Identity {
        @Test
        void identityHasPartiesAndPendingOutcome() {
            var state = projection.identity();
            assertThat(state.parties()).containsExactlyInAnyOrder("alice", "bob");
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);
            assertThat(state.proposals()).isEmpty();
            assertThat(state.responses()).isEmpty();
        }
    }

    @Nested
    class ProposeHandling {
        @Test
        void proposalCreatesActiveProposal() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "Price: $100", "p1", T1));
            assertThat(state.activeProposal()).isNotNull();
            assertThat(state.activeProposal().proposalId()).isEqualTo("p1");
            assertThat(state.activeProposal().content()).isEqualTo("Price: $100");
            assertThat(state.activeProposal().proposer()).isEqualTo("alice");
        }

        @Test
        void proposeWithoutCorrelationIdIsIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "x", null, T1));
            assertThat(state.hasActiveProposal()).isFalse();
        }

        @Test
        void counterProposalSupersedesPrevious() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.PROPOSE, "bob", "$80", "p2", T2));
            assertThat(state.activeProposal().proposalId()).isEqualTo("p2");
            assertThat(state.proposals().get(0).status()).isEqualTo(ProposalStatus.SUPERSEDED);
        }

        @Test
        void proposeWithNullContentUsesEmptyString() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", null, "p1", T1));
            assertThat(state.activeProposal().content()).isEmpty();
        }
    }

    @Nested
    class AcceptHandling {
        @Test
        void doneAcceptsAndTriggersAgreement() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DONE, "bob", null, "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.AGREED);
            assertThat(state.proposals().get(0).status()).isEqualTo(ProposalStatus.ACCEPTED);
        }

        @Test
        void doneMismatchedCorrelationIdIsIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DONE, "bob", null, "wrong", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);
        }

        @Test
        void doneWithNoActiveProposalIsIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.DONE, "bob", null, "p1", T1));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);
        }

        @Test
        void multilateralRequiresAllParties() {
            var multiProjection = new NegotiationProjection(
                    Set.of("mediator", "a", "b", "c"), new UnanimousAcceptance());
            var state = multiProjection.identity();
            state = multiProjection.apply(state, msg(MessageType.PROPOSE, "mediator", "split", "p1", T1));
            state = multiProjection.apply(state, msg(MessageType.DONE, "a", null, "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);
            state = multiProjection.apply(state, msg(MessageType.DONE, "b", null, "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);
            state = multiProjection.apply(state, msg(MessageType.DONE, "c", null, "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.AGREED);
        }
    }

    @Nested
    class DeclineHandling {
        @Test
        void declineRecordsRejection() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DECLINE, "bob", "too much", "p1", T2));
            assertThat(state.responses()).containsKey("bob");
            assertThat(state.responses().get("bob").decision()).isEqualTo(PartyDecision.REJECTED);
            assertThat(state.responses().get("bob").reason()).isEqualTo("too much");
        }

        @Test
        void declineFromProposerIsWithdrawal() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DECLINE, "alice", "changed mind", "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.WITHDRAWN);
        }

        @Test
        void declineMismatchedCorrelationIdIsIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DECLINE, "bob", "no", "wrong", T2));
            assertThat(state.responses()).isEmpty();
        }
    }

    @Nested
    class TerminalState {
        @Test
        void messagesAfterAgreedAreIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DONE, "bob", null, "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.AGREED);
            var agreed = state;
            state = projection.apply(state, msg(MessageType.PROPOSE, "bob", "$50", "p2", T3));
            assertThat(state).isEqualTo(agreed);
        }

        @Test
        void messagesAfterWithdrawnAreIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DECLINE, "alice", "quit", "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.WITHDRAWN);
            var withdrawn = state;
            state = projection.apply(state, msg(MessageType.PROPOSE, "bob", "$50", "p2", T3));
            assertThat(state).isEqualTo(withdrawn);
        }
    }

    @Nested
    class UnknownSender {
        @Test
        void proposeFromUnknownPartyIsIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "unknown", "$100", "p1", T1));
            assertThat(state.hasActiveProposal()).isFalse();
        }

        @Test
        void doneFromUnknownPartyIsIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.PROPOSE, "alice", "$100", "p1", T1));
            state = projection.apply(state, msg(MessageType.DONE, "unknown", null, "p1", T2));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);
        }
    }

    @Nested
    class NonNegotiationMessages {
        @Test
        void statusMessagesAreIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.STATUS, "alice", "update", null, T1));
            assertThat(state).isEqualTo(projection.identity());
        }

        @Test
        void commandMessagesAreIgnored() {
            var state = projection.identity();
            state = projection.apply(state, msg(MessageType.COMMAND, "alice", "do this", "c1", T1));
            assertThat(state.hasActiveProposal()).isFalse();
        }
    }

    @Nested
    class ExceptionSafety {
        @Test
        void applyNeverThrowsOnNull() {
            var state = projection.identity();
            var result = projection.apply(state, null);
            assertThat(result).isEqualTo(state);
        }
    }
}
