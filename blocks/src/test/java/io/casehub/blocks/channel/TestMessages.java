package io.casehub.blocks.channel;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class TestMessages {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private TestMessages() {}

    public static MessageReceivedEvent received(UUID channelId, MessageType type,
                                                String content, String correlationId,
                                                String sender, long epochMillis) {
        return new MessageReceivedEvent(ID_SEQ.getAndIncrement(), "test-channel",
                                        channelId, "tenant-1", type, sender, null,
                                        io.casehub.platform.api.identity.ActorType.AGENT,
                                        correlationId, Instant.ofEpochMilli(epochMillis), content, null, "general");
    }

    public static MessageReceivedEvent received(UUID channelId, MessageType type,
                                                String content, String correlationId,
                                                String sender) {
        return received(channelId, type, content, correlationId, sender, 42_000);
    }
}
