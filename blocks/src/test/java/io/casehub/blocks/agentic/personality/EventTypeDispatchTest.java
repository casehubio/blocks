package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionProfileStore;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.SignalValence;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventTypeDispatchTest {

    @Test
    void recordMatchesEventToCorrectSource() {
        var stringMatched = new AtomicBoolean();
        var intMatched = new AtomicBoolean();

        TraitPressureSource<String> stringSrc = new TraitPressureSource<>() {
            @Override public Class<String> eventType() { return String.class; }
            @Override public List<TraitActivation> translate(String e, AgentDescriptor d) {
                stringMatched.set(true);
                return List.of(new TraitActivation("ti", SignalValence.POSITIVE));
            }
        };
        TraitPressureSource<Integer> intSrc = new TraitPressureSource<>() {
            @Override public Class<Integer> eventType() { return Integer.class; }
            @Override public List<TraitActivation> translate(Integer e, AgentDescriptor d) {
                intMatched.set(true);
                return List.of(new TraitActivation("ne", SignalValence.NEGATIVE));
            }
        };

        @SuppressWarnings("unchecked")
        Instance<TraitPressureSource<?>> sources = mock(Instance.class);
        when(sources.stream()).thenReturn(java.util.stream.Stream.of(stringSrc, intSrc));

        var signalStore = mock(DispositionSignalStore.class);
        var orch = new PersonalityEvolutionOrchestrator(
                signalStore, mock(DispositionHealth.class), mock(DispositionEvolution.class),
                mock(DispositionProfileStore.class), mock(CbrCaseMemoryStore.class),
                sources, PersonalityEvolutionConfig.defaults());

        var descriptor = mock(AgentDescriptor.class);
        when(descriptor.agentId()).thenReturn("a");
        when(descriptor.tenancyId()).thenReturn("t");
        var disposition = mock(AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(
                List.of(new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));
        when(descriptor.disposition()).thenReturn(disposition);

        orch.record(42, descriptor);

        assertThat(intMatched.get()).isTrue();
        assertThat(stringMatched.get()).isFalse();
    }
}
