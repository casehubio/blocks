package io.casehub.blocks.channel;

import java.util.UUID;

public record ChannelAgentRequest(
        UUID channelId,
        String correlationId,
        io.casehub.qhorus.api.gateway.OutboundMessage message,
        String topic) {}
