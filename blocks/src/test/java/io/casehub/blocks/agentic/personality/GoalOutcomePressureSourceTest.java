package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.GoalOutcomeCounts;
import io.casehub.eidos.api.SignalValence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalOutcomePressureSourceTest {

    private AgentDescriptor descriptorWithProfile(List<DispositionValue> profile) {
        var descriptor = mock(AgentDescriptor.class);
        var disposition = mock(AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(profile);
        when(descriptor.disposition()).thenReturn(disposition);
        return descriptor;
    }

    @Test
    void eventTypeIsGoalOutcomeCounts() {
        var source = new GoalOutcomePressureSource();
        assertThat(source.eventType()).isEqualTo(GoalOutcomeCounts.class);
    }

    @Test
    void highSuccessRateActivatesDominantPositive() {
        var source = new GoalOutcomePressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));

        var result = source.translate(new GoalOutcomeCounts(8, 2), descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("ti");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.POSITIVE);
    }

    @Test
    void lowSuccessRateActivatesAuxiliaryNegative() {
        var source = new GoalOutcomePressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));

        var result = source.translate(new GoalOutcomeCounts(2, 8), descriptor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionTerm()).isEqualTo("ne");
        assertThat(result.get(0).valence()).isEqualTo(SignalValence.NEGATIVE);
    }

    @Test
    void midRangeSuccessRateReturnsEmpty() {
        var source = new GoalOutcomePressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20)));

        var result = source.translate(new GoalOutcomeCounts(5, 5), descriptor);

        assertThat(result).isEmpty();
    }

    @Test
    void zeroCasesReturnsEmpty() {
        var source = new GoalOutcomePressureSource();
        var descriptor = descriptorWithProfile(List.of(
                new DispositionValue("ti", 0.35)));

        var result = source.translate(new GoalOutcomeCounts(0, 0), descriptor);

        assertThat(result).isEmpty();
    }

    @Test
    void emptyProfileReturnsEmpty() {
        var source = new GoalOutcomePressureSource();
        var descriptor = descriptorWithProfile(List.of());

        var result = source.translate(new GoalOutcomeCounts(8, 2), descriptor);

        assertThat(result).isEmpty();
    }
}
