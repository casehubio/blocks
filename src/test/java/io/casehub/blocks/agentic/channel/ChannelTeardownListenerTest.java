package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class ChannelTeardownListenerTest {

    @Test
    void deletesChannelOnComplete() {
        var manager = mock(ChannelManager.class);
        var listener = new ChannelTeardownListener(manager);
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        listener.setBinding(binding);

        listener.onExecutionComplete(new ExecutionResult.Completed("done"),
                Duration.ofSeconds(1), 1);

        verify(manager).delete(binding.channelId(), true);
    }

    @Test
    void deletesChannelOnFailure() {
        var manager = mock(ChannelManager.class);
        var listener = new ChannelTeardownListener(manager);
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.COLLECT);
        listener.setBinding(binding);

        listener.onExecutionComplete(new ExecutionResult.Failed("err", null),
                Duration.ofSeconds(1), 1);

        verify(manager).delete(binding.channelId(), true);
    }

    @Test
    void skipsCleanupWhenNoBinding() {
        var manager = mock(ChannelManager.class);
        var listener = new ChannelTeardownListener(manager);

        listener.onExecutionComplete(new ExecutionResult.Completed("done"),
                Duration.ofSeconds(1), 1);

        verifyNoInteractions(manager);
    }

    @Test
    void swallowsDeleteException() {
        var manager = mock(ChannelManager.class);
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        when(manager.delete(any(), anyBoolean())).thenThrow(new RuntimeException("gone"));
        var listener = new ChannelTeardownListener(manager);
        listener.setBinding(binding);

        assertThatCode(() -> listener.onExecutionComplete(
                new ExecutionResult.Failed("err", null), Duration.ofSeconds(1), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void bindingAccessor() {
        var listener = new ChannelTeardownListener(mock(ChannelManager.class));
        assertThat(listener.binding()).isNull();

        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.BARRIER);
        listener.setBinding(binding);
        assertThat(listener.binding()).isSameAs(binding);
    }
}
