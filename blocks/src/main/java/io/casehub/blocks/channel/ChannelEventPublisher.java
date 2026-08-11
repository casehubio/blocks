package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;

import java.util.function.Function;
import java.util.logging.Logger;

public class ChannelEventPublisher<E> {

    private static final Logger LOG = Logger.getLogger(ChannelEventPublisher.class.getName());

    public ChannelEventPublisher(EventStreamBus<E> inputBus,
                                 MessageDispatcher dispatcher,
                                 Function<LevelEvent<E>, MessageDispatch> messageBuilder) {
        inputBus.subscribe(e -> true, event -> {
            try {
                MessageDispatch dispatch = messageBuilder.apply(event);
                if (dispatch == null) return;
                dispatcher.dispatch(dispatch);
            } catch (Exception e1) {
                LOG.warning("ChannelEventPublisher: dispatch failed: "
                    + e1.getClass().getSimpleName() + " — " + e1.getMessage());
            }
        });
    }
}
