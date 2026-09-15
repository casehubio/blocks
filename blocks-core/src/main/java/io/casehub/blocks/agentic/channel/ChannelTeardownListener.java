package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.qhorus.api.channel.ChannelManager;

import java.time.Duration;

public class ChannelTeardownListener implements ExecutionEventListener {

    private static final System.Logger LOG = System.getLogger(ChannelTeardownListener.class.getName());

    private final ChannelManager channelManager;
    private volatile ChannelBinding binding;

    public ChannelTeardownListener(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    public void setBinding(ChannelBinding binding) {
        this.binding = binding;
    }

    public ChannelBinding binding() {
        return binding;
    }

    @Override
    public void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                     int iterationCount) {
        if (binding == null) {
            LOG.log(System.Logger.Level.WARNING,
                    "ChannelTeardownListener fired with no binding — external driver did not call setBinding()");
            return;
        }
        try {
            channelManager.delete(binding.channelId(), true);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Channel cleanup failed for {0}: {1}", binding.channelId(), e.getMessage());
        }
    }
}
