package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;

import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

public class ChannelEventAdapter<E> implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(ChannelEventAdapter.class.getName());

    private final Function<MessageReceivedEvent, E> extractor;
    private final EventLevel level;
    private final EventStreamBus<E> outputBus;
    private final Set<String> channelNames;

    public ChannelEventAdapter(Function<MessageReceivedEvent, E> extractor,
                               EventLevel level,
                               EventStreamBus<E> outputBus) {
        this(extractor, level, outputBus, Set.of());
    }

    public ChannelEventAdapter(Function<MessageReceivedEvent, E> extractor,
                               EventLevel level,
                               EventStreamBus<E> outputBus,
                               Set<String> channelNames) {
        this.extractor = extractor;
        this.level = level;
        this.outputBus = outputBus;
        this.channelNames = Set.copyOf(channelNames);
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        E extracted;
        try {
            extracted = extractor.apply(event);
        } catch (Exception e) {
            LOG.warning("ChannelEventAdapter: extractor failed on channel "
                + event.channelName() + " [" + event.messageType() + "]: "
                + e.getClass().getSimpleName() + " — " + e.getMessage());
            return;
        }
        if (extracted == null) return;
        outputBus.publish(new LevelEvent<>(extracted,
            event.occurredAt().toEpochMilli(), level));
    }

    @Override
    public Set<String> channels() {
        return channelNames;
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }
}
