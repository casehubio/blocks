package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.conversation.ConversationState;

import java.util.List;

public class AddressedTurnPolicy implements TurnPolicy {

    @Override
    public List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants) {
        if (context.targetId() == null) return List.of();
        return participants.stream()
                .filter(p -> p.role().equals(context.targetId()))
                .toList();
    }
}
