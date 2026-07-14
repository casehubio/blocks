package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelEventPublisherTest {

    private static final EventLevel LEVEL = new EventLevel("test", 1);
    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private MessageDispatch buildDispatch(String content) {
        return MessageDispatch.builder()
            .channelId(CHANNEL_ID)
            .sender("test-publisher")
            .type(MessageType.STATUS)
            .content(content)
            .actorType(ActorType.AGENT)
            .build();
    }

    private DispatchResult okResult(MessageDispatch d) {
        return new DispatchResult(1L, d.channelId(), d.sender(), d.type(),
            d.correlationId(), d.inReplyTo(), d.artefactRefs(), d.target(),
            null, null, null, 0, List.of());
    }

    @Test
    void subscribesOnConstruction_dispatchesOnPublish() {
        var bus = new EventStreamBus<String>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return okResult(d);
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> buildDispatch("content:" + event.payload()));

        bus.publish(new LevelEvent<>("hello", 1, LEVEL));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).content()).isEqualTo("content:hello");
    }

    @Test
    void messageBuilderCalledPerEvent() {
        var bus = new EventStreamBus<String>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return okResult(d);
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> buildDispatch(event.payload()));

        bus.publish(new LevelEvent<>("first", 1, LEVEL));
        bus.publish(new LevelEvent<>("second", 2, LEVEL));

        assertThat(dispatched).hasSize(2);
        assertThat(dispatched.get(0).content()).isEqualTo("first");
        assertThat(dispatched.get(1).content()).isEqualTo("second");
    }

    @Test
    void dispatchException_caughtAndLogged_neverPropagated() {
        var bus = new EventStreamBus<String>();
        MessageDispatcher dispatcher = d -> {
            throw new RuntimeException("connection refused");
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> buildDispatch(event.payload()));

        bus.publish(new LevelEvent<>("hello", 1, LEVEL));
        bus.publish(new LevelEvent<>("world", 2, LEVEL));
    }

    @Test
    void messageBuilderReturnsNull_dispatchSkipped() {
        var bus = new EventStreamBus<String>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return okResult(d);
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> event.payload().equals("skip") ? null : buildDispatch(event.payload()));

        bus.publish(new LevelEvent<>("keep", 1, LEVEL));
        bus.publish(new LevelEvent<>("skip", 2, LEVEL));
        bus.publish(new LevelEvent<>("keep2", 3, LEVEL));

        assertThat(dispatched).hasSize(2);
        assertThat(dispatched.get(0).content()).isEqualTo("keep");
        assertThat(dispatched.get(1).content()).isEqualTo("keep2");
    }
}
