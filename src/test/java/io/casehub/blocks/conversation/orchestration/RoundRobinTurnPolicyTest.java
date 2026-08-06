package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.conversation.ConversationFold;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.PointClassification;
import io.casehub.blocks.conversation.Priority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinTurnPolicyTest {

    private final AgentParticipant alice = new AgentParticipant(
            AgentRef.external("alice", i -> null), "REV", "");
    private final AgentParticipant bob = new AgentParticipant(
            AgentRef.external("bob", i -> null), "IMP", "");
    private final List<AgentParticipant> participants = List.of(alice, bob);
    private final TurnPolicy policy = new RoundRobinTurnPolicy();

    @Test
    void emptyState_returnsFirstNonSender() {
        var state = emptyState();
        var ctx = new TurnContext("alice", null, "RAISE", Map.of());
        var result = policy.nextResponders(state, ctx, participants);
        assertThat(result).containsExactly(bob);
    }

    @Test
    void afterAliceSpeaks_bobResponds() {
        var state = stateWithLastSender("alice");
        var ctx = new TurnContext("alice", null, "RAISE", Map.of());
        var result = policy.nextResponders(state, ctx, participants);
        assertThat(result).containsExactly(bob);
    }

    @Test
    void afterBobSpeaks_aliceResponds() {
        var state = stateWithLastSender("bob");
        var ctx = new TurnContext("bob", null, "COUNTER", Map.of());
        var result = policy.nextResponders(state, ctx, participants);
        assertThat(result).containsExactly(alice);
    }

    @Test
    void senderNotInParticipants_returnsFirstParticipant() {
        var state = emptyState();
        var ctx = new TurnContext("human", null, "RAISE", Map.of());
        var result = policy.nextResponders(state, ctx, participants);
        assertThat(result).containsExactly(alice);
    }

    private ConversationState emptyState() {
        return new ConversationState(Map.of(), List.of(), List.of(), Map.of());
    }

    private ConversationState stateWithLastSender(String sender) {
        var state = emptyState();
        return ConversationFold.createPoint(state, "p1", "topic",
                1L, null, sender, Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
    }
}
