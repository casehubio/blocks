package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.summarisation.observation.PartitionedDrain;

@FunctionalInterface
public interface PromptAssembler {
    String assemble(AgentParticipant agent,
                    PartitionedDrain<String> drain,
                    ConversationState state);
}
