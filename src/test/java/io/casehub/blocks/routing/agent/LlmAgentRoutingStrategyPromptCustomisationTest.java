package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.blocks.prompt.SystemPromptCustomiser;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmAgentRoutingStrategyPromptCustomisationTest {

    @Mock AgentProvider agentProvider;

    private AgentRoutingContext context() {
        return new AgentRoutingContext(
                UUID.randomUUID(), "triage", NullNode.instance, "tenant", List.of(), null, null);
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of("triage"), 0, AgentHealth.READY, null, new MatchDegree.None());
    }

    @Test
    void usesCustomisedSystemPromptWhenCustomiserProvided() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("{\"agent\": \"agent-a\", \"reason\": \"best fit\"}")));

        SystemPromptCustomiser customiser = (base, sigId, slot) ->
                base + "\n\nAlways prefer agents with clinical expertise.";

        var strategy = new LlmAgentRoutingStrategy(agentProvider, null, null, null,
                new RoutingPromptAssembler(List.of()), customiser);

        strategy.select(context(), List.of(candidate("agent-a")));

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().systemPrompt())
                .contains("Always prefer agents with clinical expertise.");
    }

    @Test
    void usesDefaultSystemPromptWhenNoCustomiser() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("{\"agent\": \"agent-a\", \"reason\": \"best fit\"}")));

        var strategy = new LlmAgentRoutingStrategy(agentProvider, null, null, null,
                new RoutingPromptAssembler(List.of()), null);

        strategy.select(context(), List.of(candidate("agent-a")));

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().systemPrompt()).isEqualTo(RoutingSupport.SYSTEM_PROMPT);
    }
}
