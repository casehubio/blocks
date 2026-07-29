package io.casehub.blocks.channel.summary;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmChannelSummariserTest {

    private final AgentProvider agentProvider = mock(AgentProvider.class);
    private final LlmChannelSummariser summariser =
            new LlmChannelSummariser(agentProvider, SummaryMode.EDIT);

    @Test
    void emptyMessages_returnsCurrentSummaryWithoutLlmCall() {
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "test-channel", "tenant-1",
                SummaryResult.ofText("existing summary"), 10L, 0,
                List.of(), q -> List.of());

        assertThat(summariser.update(ctx).text()).isEqualTo("existing summary");
        verifyNoInteractions(agentProvider);
    }

    @Test
    void editMode_promptContainsCurrentSummaryAndMessages() {
        var msg = message("alice", "We decided on Redis.");
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "dev-channel", "tenant-1",
                SummaryResult.ofText("Discussing caching options."), null, 1,
                List.of(msg), q -> List.of());

        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("Updated summary.")));

        var result = summariser.update(ctx);
        assertThat(result.text()).isEqualTo("Updated summary.");

        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());

        var config = configCaptor.getValue();
        assertThat(config.systemPrompt()).contains("rewrite");
        assertThat(config.userPrompt()).contains("Discussing caching options.");
        assertThat(config.userPrompt()).contains("[alice] We decided on Redis.");
    }

    @Test
    void appendMode_promptDoesNotInstructRewrite() {
        var appendSummariser = new LlmChannelSummariser(agentProvider, SummaryMode.APPEND);
        var msg = message("bob", "Agreed.");
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "dev", "tenant-1",
                SummaryResult.ofText("Prior summary."), null, 1,
                List.of(msg), q -> List.of());

        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("Appended.")));

        appendSummariser.update(ctx);

        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());
        assertThat(configCaptor.getValue().systemPrompt()).contains("Do not modify");
        assertThat(configCaptor.getValue().systemPrompt()).doesNotContain("rewrite");
    }

    @Test
    void nullCurrentSummary_promptOmitsSummarySection() {
        var msg = message("alice", "Hello");
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "new-channel", "tenant-1",
                null, null, 1,
                List.of(msg), q -> List.of());

        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("Fresh summary.")));

        summariser.update(ctx);

        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());
        assertThat(configCaptor.getValue().userPrompt()).doesNotContain("Current summary:");
    }

    @Test
    void agentProviderFailure_propagatesException() {
        var msg = message("alice", "Hello");
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "channel", "tenant-1",
                null, null, 1,
                List.of(msg), q -> List.of());

        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().failure(
                        new RuntimeException("LLM unavailable")));

        assertThatThrownBy(() -> summariser.update(ctx))
                .hasMessageContaining("LLM unavailable");
    }

    private static Message message(String sender, String content) {
        return Message.builder()
                .id(1L).channelId(UUID.randomUUID())
                .sender(sender).content(content)
                .messageType(MessageType.RESPONSE)
                .createdAt(Instant.now()).build();
    }
}
