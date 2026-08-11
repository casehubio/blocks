package io.casehub.blocks.channel;

import java.util.function.ToIntFunction;

import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;

/**
 * Decorator that skips messages whose extracted value exceeds a bound.
 * Enables "replay state up to round N" queries without modifying the base projection.
 *
 * @param <S> the projection state type
 */
public class BoundedProjectionDecorator<S> implements ChannelProjection<S> {

    private final int maxValue;
    private final ChannelProjection<S> delegate;
    private final ToIntFunction<MessageView> valueExtractor;

    /**
     * @param maxValue       messages with extracted value above this are skipped
     * @param delegate       the base projection to fold into
     * @param valueExtractor extracts the bound value from each message (e.g. round number)
     */
    public BoundedProjectionDecorator(int maxValue, ChannelProjection<S> delegate,
                                      ToIntFunction<MessageView> valueExtractor) {
        this.maxValue = maxValue;
        this.delegate = delegate;
        this.valueExtractor = valueExtractor;
    }

    @Override
    public S identity() {
        return delegate.identity();
    }

    @Override
    public S apply(S state, MessageView message) {
        if (valueExtractor.applyAsInt(message) > maxValue) return state;
        return delegate.apply(state, message);
    }
}
