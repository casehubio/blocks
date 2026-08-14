package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.model.EventSource;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ChannelObserver<S> implements MessageObserver, EventSource {

    private static final System.Logger LOG =
        System.getLogger(ChannelObserver.class.getName());

    private final ChannelProjection<S> projection;
    private final AtomicReference<S> state;
    private final Set<String> channelNames;
    private volatile Consumer<DriverEvent> sink;

    private ChannelObserver(ChannelProjection<S> projection,
                            Set<String> channelNames) {
        this.projection = projection;
        this.state = new AtomicReference<>(projection.identity());
        this.channelNames = Set.copyOf(channelNames);
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        try {
            var messageView = toMessageView(event);
            state.updateAndGet(s -> projection.apply(s, messageView));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                "ChannelObserver: projection failed for channel "
                + event.channelId() + ": " + e.getMessage());
            var s = sink;
            if (s != null) {
                s.accept(DriverEvent.signal("projection-error:" + event.channelId()));
            }
            return;
        }
        var s = sink;
        if (s != null) {
            s.accept(DriverEvent.signal("channel:" + event.channelId()));
        }
    }

    @Override
    public Set<String> channels() {
        return channelNames;
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }

    @Override
    public Cancellation subscribe(Consumer<DriverEvent> sink) {
        if (this.sink != null) {
            throw new IllegalStateException(
                "ChannelObserver already has an active subscriber");
        }
        this.sink = sink;
        return Cancellation.of(() -> this.sink = null);
    }

    public S currentState() {
        return state.get();
    }

    public void reset() {
        state.set(projection.identity());
    }

    public <T> TerminationCondition<T> terminateWhen(Predicate<S> condition) {
        return ctx -> condition.test(currentState())
            ? new TerminationDecision.Complete("Channel observation")
            : TerminationDecision.Continue.INSTANCE;
    }

    public <T> TerminationCondition<T> asTermination(
            Function<S, TerminationDecision> evaluator) {
        return ctx -> evaluator.apply(currentState());
    }

    public static <S> ChannelObserver<S> of(ChannelProjection<S> projection,
                                             String channelName) {
        return new ChannelObserver<>(projection, Set.of(channelName));
    }

    public static <S> Builder<S> builder(ChannelProjection<S> projection) {
        return new Builder<>(projection);
    }

    public static final class Builder<S> {
        private final ChannelProjection<S> projection;
        private final Set<String> channelNames = new LinkedHashSet<>();

        private Builder(ChannelProjection<S> projection) {
            this.projection = projection;
        }

        public Builder<S> channel(String channelName) {
            channelNames.add(channelName);
            return this;
        }

        public ChannelObserver<S> build() {
            if (channelNames.isEmpty()) {
                throw new IllegalStateException("At least one channel required");
            }
            return new ChannelObserver<>(projection, channelNames);
        }
    }

    private static MessageView toMessageView(MessageReceivedEvent event) {
        return new MessageView(
            event.messageId(), event.channelId(), event.senderId(),
            event.messageType(), event.content(), event.correlationId(),
            null, event.target(), event.topic(),
            List.of(), event.actorType(), event.occurredAt(),
            null, 0);
    }
}
