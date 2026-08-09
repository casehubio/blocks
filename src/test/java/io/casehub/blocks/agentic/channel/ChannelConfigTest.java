package io.casehub.blocks.agentic.channel;

import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChannelConfigTest {

    @Test
    void rejectsNullChannelManager() {
        assertThatThrownBy(() -> new ChannelConfig(null, mock(MessageDispatcher.class),
                ChannelSemantic.APPEND, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channelManager");
    }

    @Test
    void rejectsNullMessageDispatcher() {
        assertThatThrownBy(() -> new ChannelConfig(mock(ChannelManager.class), null,
                ChannelSemantic.APPEND, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("messageDispatcher");
    }

    @Test
    void rejectsNullSemantic() {
        assertThatThrownBy(() -> new ChannelConfig(mock(ChannelManager.class),
                mock(MessageDispatcher.class), null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("semantic");
    }

    @Test
    void protocolsDefaultToEmptyList() {
        var config = ChannelConfig.of(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.APPEND);
        assertThat(config.protocols()).isEmpty();
    }

    @Test
    void protocolsDefensivelyCopied() {
        var protocols = new ArrayList<>(List.of("proto-1"));
        var config = new ChannelConfig(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.APPEND, protocols);
        protocols.add("proto-2");
        assertThat(config.protocols()).containsExactly("proto-1");
    }

    @Test
    void protocolsListIsImmutable() {
        var config = new ChannelConfig(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.COLLECT,
                List.of("p1"));
        assertThatThrownBy(() -> config.protocols().add("p2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void factoryMethodSetsFields() {
        var mgr = mock(ChannelManager.class);
        var disp = mock(MessageDispatcher.class);
        var config = ChannelConfig.of(mgr, disp, ChannelSemantic.BARRIER);
        assertThat(config.channelManager()).isSameAs(mgr);
        assertThat(config.messageDispatcher()).isSameAs(disp);
        assertThat(config.semantic()).isEqualTo(ChannelSemantic.BARRIER);
    }
}
