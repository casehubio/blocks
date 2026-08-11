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

class PointAddressedTurnPolicyTest {

    private final AgentParticipant rev = new AgentParticipant(
            AgentRef.external("reviewer", i -> null), "REV", "");
    private final AgentParticipant imp = new AgentParticipant(
            AgentRef.external("implementor", i -> null), "IMP", "");
    private final List<AgentParticipant> participants = List.of(rev, imp);
    private final TurnPolicy policy = new PointAddressedTurnPolicy();

    @Test
    void openPoint_raisedByRev_impShouldRespond() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "design",
                1L, null, "reviewer", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "This needs fixing");
        var ctx = new TurnContext("reviewer", null, "RAISE", Map.of());
        var result = policy.nextResponders(state, ctx, participants);
        assertThat(result).containsExactly(imp);
    }

    @Test
    void pointAlreadyResponded_notReturned() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "design",
                1L, null, "reviewer", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "Fix this");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "implementor", Instant.now(),
                "IMP", 1, "AGREE", "Will do", "AGREED");
        var ctx = new TurnContext("implementor", null, "AGREE", Map.of());
        var result = policy.nextResponders(state, ctx, participants);
        assertThat(result).isEmpty();
    }

    @Test
    void emptyState_returnsEmpty() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        var ctx = new TurnContext("reviewer", null, "RAISE", Map.of());
        assertThat(policy.nextResponders(state, ctx, participants)).isEmpty();
    }
}
