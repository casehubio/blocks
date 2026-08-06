package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.conversation.ConversationState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FreeTurnPolicyTest {

    private final AgentParticipant alice = new AgentParticipant(
            AgentRef.external("alice", i -> null), "REV", "");
    private final AgentParticipant bob = new AgentParticipant(
            AgentRef.external("bob", i -> null), "IMP", "");
    private final AgentParticipant carol = new AgentParticipant(
            AgentRef.external("carol", i -> null), "SUP", "");
    private final TurnPolicy policy = new FreeTurnPolicy();
    private final ConversationState empty = new ConversationState(
            Map.of(), List.of(), List.of(), Map.of());

    @Test
    void returnsAllExceptSender() {
        var ctx = new TurnContext("alice", null, "RAISE", Map.of());
        assertThat(policy.nextResponders(empty, ctx, List.of(alice, bob, carol)))
                .containsExactly(bob, carol);
    }

    @Test
    void singleParticipant_senderExcluded_returnsEmpty() {
        var ctx = new TurnContext("alice", null, "RAISE", Map.of());
        assertThat(policy.nextResponders(empty, ctx, List.of(alice))).isEmpty();
    }

    @Test
    void unknownSender_returnsAll() {
        var ctx = new TurnContext("human", null, "RAISE", Map.of());
        assertThat(policy.nextResponders(empty, ctx, List.of(alice, bob)))
                .containsExactly(alice, bob);
    }
}
