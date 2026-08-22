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
import io.casehub.eidos.api.SignalValence;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HaltFlagTest {

    private DispositionSignalStore signalStore;
    private DispositionHealth health;
    private AgentDescriptor descriptor;
    private ProbeContext probeContext;
    private AtomicInteger recordCount;

    @BeforeEach
    void setUp() {
        recordCount = new AtomicInteger();
        signalStore = mock(DispositionSignalStore.class);
        doAnswer(inv -> { recordCount.incrementAndGet(); return null; })
                .when(signalStore).recordActivation(anyString(), anyString(), anyString(), any(SignalValence.class));
        health = mock(DispositionHealth.class);

        descriptor = mock(AgentDescriptor.class);
        when(descriptor.agentId()).thenReturn("agent-1");
        when(descriptor.tenancyId()).thenReturn("tenant-1");
        var disposition = mock(AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(
                List.of(new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));
        when(descriptor.disposition()).thenReturn(disposition);

        probeContext = mock(ProbeContext.class);
    }

    private PersonalityEvolutionOrchestrator orchestratorWith(
            DispositionEvolution evolution, TraitPressureSource<?>... extraSources) {
        @SuppressWarnings("unchecked")
        Instance<TraitPressureSource<?>> sources = mock(Instance.class);
        when(sources.stream()).thenReturn(java.util.stream.Stream.of(extraSources));
        return new PersonalityEvolutionOrchestrator(
                signalStore, health, evolution, mock(DispositionProfileStore.class),
                mock(CbrCaseMemoryStore.class), sources, PersonalityEvolutionConfig.defaults());
    }

    private TraitPressureSource<String> stringSource() {
        return new TraitPressureSource<>() {
            @Override public Class<String> eventType() { return String.class; }
            @Override public List<TraitActivation> translate(String e, AgentDescriptor d) {
                return List.of(new TraitActivation("ti", SignalValence.POSITIVE));
            }
        };
    }

    @Test
    void recordingStopsWhenHalted() {
        var orch = orchestratorWith(mock(DispositionEvolution.class), stringSource());
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.Drifted(Map.of("ti", 0.55), "ti", 0.20));

        orch.tick(descriptor, probeContext);

        recordCount.set(0);
        orch.record("event", descriptor);
        assertThat(recordCount.get()).isZero();
    }

    @Test
    void recordingResumesAfterSubCeilingDrift() {
        var orch = orchestratorWith(mock(DispositionEvolution.class), stringSource());
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.Drifted(Map.of("ti", 0.55), "ti", 0.20))
                .thenReturn(new DispositionStatus.Drifted(Map.of("ti", 0.38), "ti", 0.08));

        orch.tick(descriptor, probeContext);
        orch.tick(descriptor, probeContext);

        recordCount.set(0);
        orch.record("event", descriptor);
        assertThat(recordCount.get()).isEqualTo(1);
    }

    @Test
    void dampenedSetsHaltFlag() {
        var evolution = mock(DispositionEvolution.class);
        when(evolution.evaluate(eq(descriptor), any()))
                .thenReturn(new EvolutionResult.Dampened(0.2));
        var orch = orchestratorWith(evolution, stringSource());

        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.EvolutionPending(
                        () -> "DOMINANT_AUXILIARY_SWAP", "ne", Map.of()));

        orch.tick(descriptor, probeContext);

        recordCount.set(0);
        orch.record("event", descriptor);
        assertThat(recordCount.get()).isZero();
    }

    @Test
    void evolvedClearsHaltFlag() {
        var evolution = mock(DispositionEvolution.class);
        var newProfile = List.of(new DispositionValue("ne", 0.35), new DispositionValue("ti", 0.20));
        when(evolution.evaluate(eq(descriptor), any()))
                .thenReturn(new EvolutionResult.Evolved(newProfile, "TI-NE", "NE-TI"));
        var orch = orchestratorWith(evolution, stringSource());

        // First halt via ceiling
        when(health.probe(descriptor, probeContext))
                .thenReturn(new DispositionStatus.Drifted(Map.of("ti", 0.55), "ti", 0.20))
                .thenReturn(new DispositionStatus.EvolutionPending(
                        () -> "DOMINANT_AUXILIARY_SWAP", "ne", Map.of()));

        orch.tick(descriptor, probeContext); // halted
        orch.tick(descriptor, probeContext); // evolved

        recordCount.set(0);
        orch.record("event", descriptor);
        assertThat(recordCount.get()).isEqualTo(1);
    }
}
