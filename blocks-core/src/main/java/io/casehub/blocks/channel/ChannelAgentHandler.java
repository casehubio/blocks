package io.casehub.blocks.channel;

import io.casehub.qhorus.api.message.MessageDispatch;

import java.util.UUID;

/**
 * SPI for channel-reactive agent handlers.
 * Handlers must have non-overlapping handles() predicates — first-match routing.
 */
public interface ChannelAgentHandler {

    boolean handles(ChannelAgentRequest request);

    AgentTask prepareTask(ChannelAgentRequest request);

    MessageDispatch buildResponse(UUID channelId, String senderId,
                                  String llmOutput, ChannelAgentRequest trigger);
}
