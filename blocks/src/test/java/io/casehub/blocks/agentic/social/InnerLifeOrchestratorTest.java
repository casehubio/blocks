package io.casehub.blocks.agentic.social;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InnerLifeOrchestratorTest {

    private ReflectionOrchestrator reflectionOrchestrator;
    private AgentProvider agentProvider;
    private AgentDescriptor descriptor;

    @BeforeEach
    void setUp() {
        reflectionOrchestrator = mock(ReflectionOrchestrator.class);
        agentProvider = mock(AgentProvider.class);
        descriptor = mock(AgentDescriptor.class);
        when(descriptor.agentId()).thenReturn("agent-1");
        when(descriptor.tenancyId()).thenReturn("tenant-1");
        when(descriptor.name()).thenReturn("TestAgent");
        when(descriptor.briefing()).thenReturn("A test agent");
        var disposition = mock(AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(List.of());
        when(descriptor.disposition()).thenReturn(disposition);
    }

    @SuppressWarnings("unchecked")
    private InnerLifeOrchestrator makeOrchestrator(CivilityConstraint... constraints) {
        Instance<CivilityConstraint> cdi = mock(Instance.class);
        when(cdi.stream()).thenReturn(java.util.stream.Stream.of(constraints));
        return new InnerLifeOrchestrator(
                reflectionOrchestrator, agentProvider, cdi,
                InnerLifeConfig.defaults());
    }

    private LevelEvent<String> event(String text) {
        return new LevelEvent<>(text, System.currentTimeMillis(), new EventLevel("L1", 1));
    }

    @Test
    void tickReturnsSilentWhenCivilityDenied() {
        var orch = makeOrchestrator(ctx -> new CivilityCheck.Denied("test deny"));

        var result = orch.tick(descriptor, "channel context");

        assertThat(result).isInstanceOf(InnerLifeTick.Silent.class);
        assertThat(((InnerLifeTick.Silent) result).reason()).contains("test deny");
        verify(reflectionOrchestrator, never()).reflect(anyString(), anyString(), any(), anyInt());
        verify(agentProvider, never()).invoke(any());
    }

    @Test
    void tickReturnsSilentWhenNoObservations() {
        var orch = makeOrchestrator(ctx -> new CivilityCheck.Permitted());

        var result = orch.tick(descriptor, "context");

        assertThat(result).isInstanceOf(InnerLifeTick.Silent.class);
        verify(reflectionOrchestrator, never()).reflect(anyString(), anyString(), any(), anyInt());
    }

    @Test
    void tickReturnsSilentWhenBelowMotivationThreshold() {
        var orch = makeOrchestrator(ctx -> new CivilityCheck.Permitted());

        for (int i = 0; i < 5; i++) {
            orch.observe(event("novel content " + i + " unique " + System.nanoTime()), descriptor);
        }

        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of("I noticed some things"));
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"score\": 0.2, \"content\": \"\", \"channelHint\": null}")));

        var result = orch.tick(descriptor, "context");

        assertThat(result).isInstanceOf(InnerLifeTick.Silent.class);
    }

    @Test
    void tickReturnsInitiatedWhenAboveMotivationThreshold() {
        var orch = makeOrchestrator(ctx -> new CivilityCheck.Permitted());

        for (int i = 0; i < 5; i++) {
            orch.observe(event("novel content " + i + " unique " + System.nanoTime()), descriptor);
        }

        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of("I have something to say"));
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"score\": 0.85, \"content\": \"Hello everyone!\", \"channelHint\": \"#general\"}")));

        var result = orch.tick(descriptor, "context");

        assertThat(result).isInstanceOf(InnerLifeTick.Initiated.class);
        var initiated = (InnerLifeTick.Initiated) result;
        assertThat(initiated.content()).isEqualTo("Hello everyone!");
        assertThat(initiated.channelHint()).isEqualTo("#general");
        assertThat(initiated.motivationScore()).isEqualTo(0.85);
    }

    @Test
    void tickReturnsSilentOnMalformedLlmResponse() {
        var orch = makeOrchestrator(ctx -> new CivilityCheck.Permitted());

        for (int i = 0; i < 5; i++) {
            orch.observe(event("novel content " + i + " unique " + System.nanoTime()), descriptor);
        }

        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of("thinking"));
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("not valid json")));

        var result = orch.tick(descriptor, "context");

        assertThat(result).isInstanceOf(InnerLifeTick.Silent.class);
        assertThat(((InnerLifeTick.Silent) result).reason()).contains("parse failure");
    }

    @Test
    void multipleTextDeltasAreConcatenated() {
        var orch = makeOrchestrator(ctx -> new CivilityCheck.Permitted());

        for (int i = 0; i < 5; i++) {
            orch.observe(event("novel content " + i + " unique " + System.nanoTime()), descriptor);
        }

        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of("thoughts"));
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"score\": 0.9, "),
                        new AgentEvent.TextDelta("\"content\": \"Hi!\", "),
                        new AgentEvent.TextDelta("\"channelHint\": null}")));

        var result = orch.tick(descriptor, "context");

        assertThat(result).isInstanceOf(InnerLifeTick.Initiated.class);
        assertThat(((InnerLifeTick.Initiated) result).content()).isEqualTo("Hi!");
    }
}
