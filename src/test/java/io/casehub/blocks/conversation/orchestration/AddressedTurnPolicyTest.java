package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.conversation.ConversationState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AddressedTurnPolicyTest {

    private final AgentParticipant rev = new AgentParticipant(
            AgentRef.external("reviewer", i -> null), "REV", "");
    private final AgentParticipant imp = new AgentParticipant(
            AgentRef.external("implementor", i -> null), "IMP", "");
    private final List<AgentParticipant> participants = List.of(rev, imp);
    private final TurnPolicy policy = new AddressedTurnPolicy();
    private final ConversationState empty = new ConversationState(
            Map.of(), List.of(), List.of(), Map.of());

    @Test
    void targetMatchesRole_returnsMatchingParticipant() {
        var ctx = new TurnContext("human", "IMP", "RAISE", Map.of());
        assertThat(policy.nextResponders(empty, ctx, participants))
                .containsExactly(imp);
    }

    @Test
    void nullTarget_returnsEmptyList() {
        var ctx = new TurnContext("human", null, "RAISE", Map.of());
        assertThat(policy.nextResponders(empty, ctx, participants)).isEmpty();
    }

    @Test
    void unknownTarget_returnsEmptyList() {
        var ctx = new TurnContext("human", "UNKNOWN", "RAISE", Map.of());
        assertThat(policy.nextResponders(empty, ctx, participants)).isEmpty();
    }
}
