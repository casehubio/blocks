package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.neocortex.memory.relationship.RelationshipEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionSignalTest {

    @Test
    void customSignalCarriesDescriptionAndQuality() {
        var signal = new InteractionSignal.CustomSignal("hello", QualitySignal.POSITIVE);
        assertThat(signal.description()).isEqualTo("hello");
        assertThat(signal.quality()).isEqualTo(QualitySignal.POSITIVE);
    }

    @Test
    void relationshipSignalDelegatesToEvent() {
        var event = new RelationshipEvent(
                "agent-1", "user-1", "t1", null, null, null, "chat",
                QualitySignal.POSITIVE, "friendly exchange", null, Map.of());
        var signal = new InteractionSignal.RelationshipSignal(event);
        assertThat(signal.description()).isEqualTo("friendly exchange");
        assertThat(signal.quality()).isEqualTo(QualitySignal.POSITIVE);
    }

    @Test
    void experienceSignalUsesProvidedQuality() {
        var event = new Observation(
                "agent-1", "t1", "case-1", "turn-1",
                null, "observed something", null, Map.of(), "user-1");
        var signal = new InteractionSignal.ExperienceSignal(event, QualitySignal.NEUTRAL);
        assertThat(signal.description()).isEqualTo("observed something");
        assertThat(signal.quality()).isEqualTo(QualitySignal.NEUTRAL);
    }

    @Test
    void sealedTypeExhaustiveness() {
        InteractionSignal signal = new InteractionSignal.CustomSignal("x", QualitySignal.NEUTRAL);
        var result = switch (signal) {
            case InteractionSignal.RelationshipSignal r -> "relationship";
            case InteractionSignal.ExperienceSignal e -> "experience";
            case InteractionSignal.CustomSignal c -> "custom";
        };
        assertThat(result).isEqualTo("custom");
    }
}
