package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.qhorus.api.message.MessageView;

@FunctionalInterface
public interface ResponseMessageBuilder {
    MessageView build(AgentParticipant agent,
                      AgentResult result,
                      ConversationState currentState);
}
