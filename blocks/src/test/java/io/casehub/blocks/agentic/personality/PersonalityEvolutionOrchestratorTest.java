package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.CapabilityHealth.ProbeContext;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionEvolution.EvolutionResult;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionHealth.DispositionStatus;
import io.casehub.eidos.api.DispositionProfileStore;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.EvolutionType;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalityEvolutionOrchestratorTest {

    private DispositionSignalStore signalStore;
    private DispositionHealth health;
    private DispositionEvolution evolution;
    private DispositionProfileStore profileStore;
    private CbrCaseMemoryStore cbrStore;
    private PersonalityEvolutionOrchestrator orchestrator;
    private AgentDescriptor descriptor;
    private ProbeContext probeContext;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        signalStore = mock(DispositionSignalStore.class);
        health = mock(DispositionHealth.class);
        evolution = mock(DispositionEvolution.class);
        profileStore = mock(DispositionProfileStore.class);
        cbrStore = mock(CbrCaseMemoryStore.class);
        Instance<TraitPressureSource<?>> sources = mock(Instance.class);
        when(sources.stream()).thenReturn(java.util.stream.Stream.empty());

        orchestrator = new PersonalityEvolutionOrchestrator(
                signalStore, health, evolution, profileStore, cbrStore, sources,
                PersonalityEvolutionConfig.defaults());

        descriptor = mock(AgentDescriptor.class);
        when(descriptor.agentId()).thenReturn("agent-1");
        when(descriptor.tenancyId()).thenReturn("tenant-1");
        var disposition = mock(AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(List.of(
                new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));
        when(descriptor.disposition()).thenReturn(disposition);

        probeContext = mock(ProbeContext.class);
    }

    @Test
    void tickReturnsStableWhenAligned() {
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.Aligned(Map.of("ti", 0.35, "ne", 0.20)));

        var result = orchestrator.tick(descriptor, probeContext);

        assertThat(result).isInstanceOf(EvolutionTick.Stable.class);
        verify(signalStore).decay("agent-1", "tenant-1", 0.8);
    }

    @Test
    void tickReturnsDriftingWhenBelowCeiling() {
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.Drifted(Map.of("ti", 0.38), "ti", 0.10));

        var result = orchestrator.tick(descriptor, probeContext);

        assertThat(result).isInstanceOf(EvolutionTick.Drifting.class);
        assertThat(((EvolutionTick.Drifting) result).magnitude()).isEqualTo(0.10);
    }

    @Test
    void tickReturnsHaltedWhenAboveCeiling() {
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.Drifted(Map.of("ti", 0.55), "ti", 0.20));

        var result = orchestrator.tick(descriptor, probeContext);

        assertThat(result).isInstanceOf(EvolutionTick.Halted.class);
        assertThat(((EvolutionTick.Halted) result).magnitude()).isEqualTo(0.20);
    }

    @Test
    void tickReturnsEvolvedAndPersistsProfile() {
        var newProfile = List.of(
                new DispositionValue("ne", 0.35), new DispositionValue("ti", 0.20));
        EvolutionType swapType = () -> "DOMINANT_AUXILIARY_SWAP";
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.EvolutionPending(
                        swapType, "ne", Map.of("ne", 0.36, "ti", 0.30)));
        when(evolution.evaluate(eq(descriptor), any()))
                .thenReturn(new EvolutionResult.Evolved(newProfile, "TI-NE", "NE-TI"));

        var result = orchestrator.tick(descriptor, probeContext);

        assertThat(result).isInstanceOf(EvolutionTick.Evolved.class);
        var evolved = (EvolutionTick.Evolved) result;
        assertThat(evolved.previousTypeLabel()).isEqualTo("TI-NE");
        assertThat(evolved.newTypeLabel()).isEqualTo("NE-TI");
        assertThat(evolved.newProfile()).hasSize(2);
        verify(profileStore).update("agent-1", "tenant-1", newProfile);
        verify(signalStore).clear("agent-1", "tenant-1");
    }

    @Test
    void tickReturnsDampenedAndDecaysAndHalts() {
        EvolutionType swapType = () -> "DOMINANT_AUXILIARY_SWAP";
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.EvolutionPending(
                        swapType, "ne", Map.of()));
        when(evolution.evaluate(eq(descriptor), any()))
                .thenReturn(new EvolutionResult.Dampened(0.2));

        var result = orchestrator.tick(descriptor, probeContext);

        assertThat(result).isInstanceOf(EvolutionTick.Dampened.class);
        assertThat(((EvolutionTick.Dampened) result).decayFactor()).isEqualTo(0.2);
        // decay called twice: once for tick decay (0.8), once for dampening (0.2)
        verify(signalStore).decay("agent-1", "tenant-1", 0.8);
        verify(signalStore).decay("agent-1", "tenant-1", 0.2);
    }
}
