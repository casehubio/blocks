package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.conversation.ConversationState;

import java.util.List;

public class RoundRobinTurnPolicy implements TurnPolicy {

    @Override
    public List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants) {
        if (participants.isEmpty()) return List.of();

        int senderIndex = -1;
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).agentId().equals(context.senderId())) {
                senderIndex = i;
                break;
            }
        }

        if (senderIndex == -1) {
            return List.of(participants.getFirst());
        }

        int nextIndex = (senderIndex + 1) % participants.size();
        return List.of(participants.get(nextIndex));
    }
}
