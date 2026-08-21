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

class NegotiationIntegrationTest {

    private static final UUID CHANNEL = UUID.randomUUID();

    private MessageView msg(MessageType type, String sender, String content,
                            String correlationId, Instant createdAt) {
        return new MessageView(1L, CHANNEL, sender, type, content, null,
                correlationId, null, null, null, List.of(), ActorType.AGENT, createdAt, null, 0);
    }

    @Nested
    class BilateralNegotiation {
        private final NegotiationProjection projection = new NegotiationProjection(
                Set.of("alice", "bob"), new UnanimousAcceptance());

        @Test
        void twoRoundCounterProposal() {
            var state = projection.identity();

            state = projection.apply(state, msg(MessageType.PROPOSE, "alice",
                    "Price: $100", "p1", Instant.parse("2026-01-01T00:00:00Z")));
            assertThat(state.round()).isEqualTo(1);
            assertThat(state.activeProposal().proposer()).isEqualTo("alice");

            state = projection.apply(state, msg(MessageType.DECLINE, "bob",
                    "Too expensive", "p1", Instant.parse("2026-01-01T00:01:00Z")));
            assertThat(state.responses().get("bob").decision()).isEqualTo(PartyDecision.REJECTED);

            state = projection.apply(state, msg(MessageType.PROPOSE, "bob",
                    "Price: $80", "p2", Instant.parse("2026-01-01T00:02:00Z")));
            assertThat(state.round()).isEqualTo(2);
            assertThat(state.activeProposal().proposer()).isEqualTo("bob");
            assertThat(state.proposals().get(0).status()).isEqualTo(ProposalStatus.SUPERSEDED);
            assertThat(state.responses()).isEmpty();

            state = projection.apply(state, msg(MessageType.DONE, "alice",
                    null, "p2", Instant.parse("2026-01-01T00:03:00Z")));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.AGREED);
            assertThat(state.proposals().get(1).status()).isEqualTo(ProposalStatus.ACCEPTED);

            var rendered = new NegotiationRenderer().render(state);
            assertThat(rendered).contains("**Status:** AGREED");
            assertThat(rendered).contains("Proposal History");
        }

        @Test
        void withdrawalEndsNegotiation() {
            var state = projection.identity();

            state = projection.apply(state, msg(MessageType.PROPOSE, "alice",
                    "$100", "p1", Instant.parse("2026-01-01T00:00:00Z")));
            state = projection.apply(state, msg(MessageType.DECLINE, "bob",
                    "No deal", "p1", Instant.parse("2026-01-01T00:01:00Z")));
            state = projection.apply(state, msg(MessageType.DECLINE, "alice",
                    "I give up", "p1", Instant.parse("2026-01-01T00:02:00Z")));

            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.WITHDRAWN);

            state = projection.apply(state, msg(MessageType.PROPOSE, "bob",
                    "$50", "p2", Instant.parse("2026-01-01T00:03:00Z")));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.WITHDRAWN);
            assertThat(state.round()).isEqualTo(1);
        }
    }

    @Nested
    class MultilateralNegotiation {
        private final NegotiationProjection projection = new NegotiationProjection(
                Set.of("mediator", "a", "b", "c"), new UnanimousAcceptance());

        @Test
        void twoRoundMediatedNegotiation() {
            var state = projection.identity();

            state = projection.apply(state, msg(MessageType.PROPOSE, "mediator",
                    "Split 50/50", "p1", Instant.parse("2026-01-01T00:00:00Z")));
            state = projection.apply(state, msg(MessageType.DONE, "a",
                    null, "p1", Instant.parse("2026-01-01T00:01:00Z")));
            state = projection.apply(state, msg(MessageType.DONE, "b",
                    null, "p1", Instant.parse("2026-01-01T00:01:30Z")));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);

            state = projection.apply(state, msg(MessageType.DECLINE, "c",
                    "Want 40/60", "p1", Instant.parse("2026-01-01T00:02:00Z")));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);

            state = projection.apply(state, msg(MessageType.PROPOSE, "mediator",
                    "Split 45/55", "p2", Instant.parse("2026-01-01T00:03:00Z")));
            assertThat(state.round()).isEqualTo(2);
            assertThat(state.responses()).isEmpty();

            state = projection.apply(state, msg(MessageType.DONE, "a",
                    null, "p2", Instant.parse("2026-01-01T00:04:00Z")));
            state = projection.apply(state, msg(MessageType.DONE, "b",
                    null, "p2", Instant.parse("2026-01-01T00:04:30Z")));
            state = projection.apply(state, msg(MessageType.DONE, "c",
                    null, "p2", Instant.parse("2026-01-01T00:05:00Z")));

            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.AGREED);
            assertThat(state.proposals().get(0).status()).isEqualTo(ProposalStatus.SUPERSEDED);
            assertThat(state.proposals().get(1).status()).isEqualTo(ProposalStatus.ACCEPTED);
        }

        @Test
        void majorityAcceptance() {
            var majorityProjection = new NegotiationProjection(
                    Set.of("mediator", "a", "b", "c"), new MajorityAcceptance());
            var state = majorityProjection.identity();

            state = majorityProjection.apply(state, msg(MessageType.PROPOSE, "mediator",
                    "Plan A", "p1", Instant.parse("2026-01-01T00:00:00Z")));
            state = majorityProjection.apply(state, msg(MessageType.DONE, "a",
                    null, "p1", Instant.parse("2026-01-01T00:01:00Z")));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.PENDING);

            state = majorityProjection.apply(state, msg(MessageType.DONE, "b",
                    null, "p1", Instant.parse("2026-01-01T00:01:30Z")));
            assertThat(state.outcome()).isEqualTo(NegotiationOutcome.AGREED);
        }
    }
}
