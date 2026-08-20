package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.BehavioralSignal;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.SignalValence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BehavioralSignalPressureSourceTest {

    private AgentDescriptor descriptorWithProfile(List<DispositionValue> profile) {
        var descriptor = mock(AgentDescriptor.class);
        var disposition = mock(AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(profile);
        when(descriptor.disposition()).thenReturn(disposition);
        return descriptor;
    }

    @Test
    void eventTypeIsBehavioralSignal() {
        var source = new BehavioralSignalPressureSource();
        assertThat(source.eventType()).isEqualTo(BehavioralSignal.class);
    }

    @Test
    void successActivatesDominantPositive() {
        var source = new BehavioralSignalPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35),
                new DispositionValue("ne", 0.20),
                new DispositionValue("fi", 0.10)));

        var result = source.translate(BehavioralSignal.SUCCESS, descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("ti");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.POSITIVE);
    }

    @Test
    void compliantActivatesDominantPositive() {
        var source = new BehavioralSignalPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("openness", 0.30),
                new DispositionValue("conscientiousness", 0.25)));

        var result = source.translate(BehavioralSignal.COMPLIANT, descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("openness");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.POSITIVE);
    }

    @Test
    void declineActivatesAuxiliaryNegative() {
        var source = new BehavioralSignalPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35),
                new DispositionValue("ne", 0.20)));

        var result = source.translate(BehavioralSignal.DECLINE, descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("ne");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.NEGATIVE);
    }

    @Test
    void violatedActivatesAuxiliaryNegative() {
        var source = new BehavioralSignalPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("dominance", 0.40),
                new DispositionValue("influence", 0.25)));

        var result = source.translate(BehavioralSignal.VIOLATED, descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("influence");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.NEGATIVE);
    }

    @Test
    void emptyProfileReturnsEmptyList() {
        var source = new BehavioralSignalPressureSource();
        var descriptor = descriptorWithProfile(List.of());

        var result = source.translate(BehavioralSignal.SUCCESS, descriptor);

        assertThat(result).isEmpty();
    }

    @Test
    void singleFunctionProfileDeclineTargetsSameFunction() {
        var source = new BehavioralSignalPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.80)));

        var result = source.translate(BehavioralSignal.DECLINE, descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("ti");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.NEGATIVE);
    }
}
