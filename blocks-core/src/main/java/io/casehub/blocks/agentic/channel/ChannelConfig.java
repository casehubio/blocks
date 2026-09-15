package io.casehub.blocks.agentic.channel;

import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatcher;

import java.util.List;
import java.util.Objects;

public record ChannelConfig(
        ChannelManager channelManager,
        MessageDispatcher messageDispatcher,
        ChannelSemantic semantic,
        List<String> protocols
) {
    public ChannelConfig {
        Objects.requireNonNull(channelManager, "channelManager");
        Objects.requireNonNull(messageDispatcher, "messageDispatcher");
        Objects.requireNonNull(semantic, "semantic");
        protocols = protocols != null ? List.copyOf(protocols) : List.of();
    }

    public static ChannelConfig of(ChannelManager channelManager,
                                    MessageDispatcher messageDispatcher,
                                    ChannelSemantic semantic) {
        return new ChannelConfig(channelManager, messageDispatcher, semantic, null);
    }
}
