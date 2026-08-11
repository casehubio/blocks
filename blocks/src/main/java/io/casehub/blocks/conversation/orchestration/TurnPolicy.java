package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.conversation.ConversationState;

import java.util.List;

public interface TurnPolicy {
    List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants);
}
