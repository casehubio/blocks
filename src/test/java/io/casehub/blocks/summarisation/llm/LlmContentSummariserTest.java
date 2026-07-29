package io.casehub.blocks.summarisation.llm;

import io.casehub.blocks.summarisation.SummaryMode;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmContentSummariserTest {

    private final AgentProvider agentProvider = mock(AgentProvider.class);

    @Test
    void editMode_sendsCurrentSummaryAndItems() {
        var summariser = new LlmContentSummariser<>(
                agentProvider, Object::toString, SummaryMode.EDIT);

        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("Updated.")));

        var prev = new SummaryResult("Prior summary.", Map.of("tier", "grouped"));
        var result = summariser.summarise(List.of("item-1", "item-2"), prev)
                .toCompletableFuture().join();

        assertThat(result.text()).isEqualTo("Updated.");
        assertThat(result.annotations())
                .containsEntry("tier", "synthesised")
                .containsEntry("itemCount", "2");

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().systemPrompt()).contains("rewrite");
        assertThat(captor.getValue().userPrompt()).contains("Prior summary.");
        assertThat(captor.getValue().userPrompt()).contains("item-1");
    }

    @Test
    void appendMode_doesNotInstructRewrite() {
        var summariser = new LlmContentSummariser<>(
                agentProvider, Object::toString, SummaryMode.APPEND);
        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("Appended.")));

        summariser.summarise(List.of("x"), null).toCompletableFuture().join();

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().systemPrompt()).contains("Do not modify");
        assertThat(captor.getValue().systemPrompt()).doesNotContain("rewrite");
    }

    @Test
    void preamble_includedInPrompt() {
        var summariser = new LlmContentSummariser<>(
                agentProvider, Object::toString, SummaryMode.EDIT, "Channel: design-review");
        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("Done.")));

        summariser.summarise(List.of("item"), null).toCompletableFuture().join();

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().userPrompt()).startsWith("Channel: design-review");
    }

    @Test
    void nullPrevious_omitsSummarySection() {
        var summariser = new LlmContentSummariser<>(
                agentProvider, Object::toString, SummaryMode.EDIT);
        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("Fresh.")));

        summariser.summarise(List.of("item"), null).toCompletableFuture().join();

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().userPrompt()).doesNotContain("Current summary:");
    }

    @Test
    void propagatesPreviousAnnotations() {
        var summariser = new LlmContentSummariser<>(
                agentProvider, Object::toString, SummaryMode.EDIT);
        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("result")));

        var prev = new SummaryResult("text", Map.of("domain", "medical"));
        var result = summariser.summarise(List.of("item"), prev)
                .toCompletableFuture().join();

        assertThat(result.annotations())
                .containsEntry("domain", "medical")
                .containsEntry("tier", "synthesised");
    }

    @Test
    void agentProviderFailure_propagates() {
        var summariser = new LlmContentSummariser<>(
                agentProvider, Object::toString, SummaryMode.EDIT);
        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));

        assertThatThrownBy(() -> summariser.summarise(List.of("item"), null)
                .toCompletableFuture().join())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM unavailable");
    }
}
