package io.casehub.blocks.channel;

import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BoundedProjectionDecoratorTest {

    @Test
    void apply_delegatesWhenValueAtOrBelowBound() {
        ChannelProjection<Integer> delegate = countingProjection();
        var bounded = new BoundedProjectionDecorator<>(3, delegate, msg -> 2);

        MessageView msg = mock(MessageView.class);
        int result = bounded.apply(0, msg);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void apply_skipsWhenValueExceedsBound() {
        ChannelProjection<Integer> delegate = countingProjection();
        var bounded = new BoundedProjectionDecorator<>(3, delegate, msg -> 4);

        MessageView msg = mock(MessageView.class);
        int result = bounded.apply(0, msg);
        assertThat(result).isEqualTo(0);
    }

    @Test
    void apply_delegatesAtExactBound() {
        ChannelProjection<Integer> delegate = countingProjection();
        var bounded = new BoundedProjectionDecorator<>(3, delegate, msg -> 3);

        MessageView msg = mock(MessageView.class);
        int result = bounded.apply(0, msg);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void apply_skipsAtBoundPlusOne() {
        ChannelProjection<Integer> delegate = countingProjection();
        var bounded = new BoundedProjectionDecorator<>(3, delegate, msg -> 4);

        MessageView msg = mock(MessageView.class);
        int result = bounded.apply(5, msg);
        assertThat(result).isEqualTo(5);
    }

    @Test
    void identity_delegatesToBaseProjection() {
        ChannelProjection<Integer> delegate = countingProjection();
        var bounded = new BoundedProjectionDecorator<>(3, delegate, msg -> 1);
        assertThat(bounded.identity()).isEqualTo(0);
    }

    @Test
    void fold_accumulatesOnlyBelowBound() {
        ChannelProjection<Integer> delegate = countingProjection();
        var bounded = new BoundedProjectionDecorator<>(2, delegate, msg -> {
            when(msg.content()).thenReturn("ignored");
            return Integer.parseInt(msg.correlationId());
        });

        int state = bounded.identity();
        for (int round = 1; round <= 4; round++) {
            MessageView msg = mock(MessageView.class);
            when(msg.correlationId()).thenReturn(String.valueOf(round));
            state = bounded.apply(state, msg);
        }
        assertThat(state).isEqualTo(2);
    }

    private ChannelProjection<Integer> countingProjection() {
        return new ChannelProjection<>() {
            @Override public Integer identity() { return 0; }
            @Override public Integer apply(Integer state, MessageView msg) { return state + 1; }
        };
    }
}
