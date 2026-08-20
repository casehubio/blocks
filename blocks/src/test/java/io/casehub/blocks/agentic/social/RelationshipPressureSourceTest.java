package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.SignalValence;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.neocortex.memory.relationship.RelationshipEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelationshipPressureSourceTest {

    private AgentDescriptor descriptorWithProfile(List<DispositionValue> profile) {
        var descriptor = mock(AgentDescriptor.class);
        var disposition = mock(AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(profile);
        when(descriptor.disposition()).thenReturn(disposition);
        return descriptor;
    }

    private RelationshipEvent eventWithQuality(QualitySignal quality) {
        return new RelationshipEvent("a1", "a2", "t1", "c1", "turn1",
                "interaction", quality, "desc", 0.5, Map.of());
    }

    @Test
    void eventTypeIsRelationshipEvent() {
        var source = new RelationshipPressureSource();
        assertThat(source.eventType()).isEqualTo(RelationshipEvent.class);
    }

    @Test
    void positiveQualityActivatesDominantPositive() {
        var source = new RelationshipPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));

        var result = source.translate(eventWithQuality(QualitySignal.POSITIVE), descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("ti");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.POSITIVE);
    }

    @Test
    void negativeQualityActivatesAuxiliaryNegative() {
        var source = new RelationshipPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));

        var result = source.translate(eventWithQuality(QualitySignal.NEGATIVE), descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("ne");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.NEGATIVE);
    }

    @Test
    void neutralQualityReturnsEmpty() {
        var source = new RelationshipPressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));

        var result = source.translate(eventWithQuality(QualitySignal.NEUTRAL), descriptor);

        assertThat(result).isEmpty();
    }

    @Test
    void emptyProfileReturnsEmpty() {
        var source = new RelationshipPressureSource();
        var descriptor = descriptorWithProfile(List.of());

        var result = source.translate(eventWithQuality(QualitySignal.POSITIVE), descriptor);

        assertThat(result).isEmpty();
    }
}
