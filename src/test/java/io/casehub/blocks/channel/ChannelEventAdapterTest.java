package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelEventAdapterTest {

    private static final EventLevel LEVEL = new EventLevel("channel", 1);
    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private MessageReceivedEvent event(MessageType type, String content,
                                       String correlationId, String sender) {
        return new MessageReceivedEvent("test-channel", CHANNEL_ID, "tenant-1",
            type, sender, correlationId, Instant.ofEpochMilli(42_000), content, "general");
    }

    @Test
    void onMessage_extractorCalled_eventPublished() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).isEqualTo("hello");
    }

    @Test
    void onMessage_timestampFromOccurredAt() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received.get(0).timestamp()).isEqualTo(42_000L);
    }

    @Test
    void onMessage_levelSetCorrectly() {
        var level = new EventLevel("classified", 2);
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> e.content(), level, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received.get(0).level()).isEqualTo(level);
    }

    @Test
    void onMessage_extractorReturnsNull_noEventPublished() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> null, LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received).isEmpty();
    }

    @Test
    void onMessage_eventTypeWithNullContent_handledByExtractor() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(
            e -> e.messageType() == MessageType.EVENT ? null : e.content(),
            LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.EVENT, null, null, "system"));

        assertThat(received).as("EVENT with null content filtered").isEmpty();
    }

    @Test
    void onMessage_extractorThrows_eventDroppedSilently() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(
            e -> { throw new RuntimeException("parse error"); },
            LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received).as("exception caught, event dropped").isEmpty();
    }

    @Test
    void channels_threeArgConstructor_returnsEmptySet() {
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL,
            new EventStreamBus<>());
        assertThat(adapter.channels()).isEmpty();
    }

    @Test
    void channels_fourArgConstructor_returnsSpecifiedChannels() {
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL,
            new EventStreamBus<>(), Set.of("channel-a", "channel-b"));
        assertThat(adapter.channels()).containsExactlyInAnyOrder("channel-a", "channel-b");
    }

    @Test
    void scope_returnsLocal() {
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL,
            new EventStreamBus<>());
        assertThat(adapter.scope()).isEqualTo(MessageObserver.Scope.LOCAL);
    }
}
