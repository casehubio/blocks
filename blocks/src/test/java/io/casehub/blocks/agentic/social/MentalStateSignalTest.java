package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.neocortex.memory.relationship.RelationshipEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentalStateSignalTest {

    @Test
    void verbalCue() {
        var signal = new MentalStateSignal.VerbalCue("I think deployments are risky",
                CueType.BELIEF_STATEMENT);
        assertThat(signal.content()).isEqualTo("I think deployments are risky");
        assertThat(signal.type()).isEqualTo(CueType.BELIEF_STATEMENT);
    }

    @Test
    void behavioralCue() {
        var signal = new MentalStateSignal.BehavioralCue(
                "subject checked dashboard 5 times", "dashboard_check");
        assertThat(signal.content()).contains("dashboard");
        assertThat(signal.actionType()).isEqualTo("dashboard_check");
    }

    @Test
    void contextualCue() {
        var signal = new MentalStateSignal.ContextualCue(
                "deadline approaching", Map.of("deadline", "2026-08-25"));
        assertThat(signal.content()).contains("deadline");
        assertThat(signal.metadata()).containsKey("deadline");
    }

    @Test
    void contextualCueMetadataDefensivelyCopied() {
        var meta = new java.util.HashMap<>(Map.of("k", "v"));
        var signal = new MentalStateSignal.ContextualCue("text", meta);
        assertThatThrownBy(() -> signal.metadata().put("k2", "v2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void relationshipCue() {
        var event = new RelationshipEvent("agent1", "user1", "tenant1",
                "case1", "turn1", "conversation",
                QualitySignal.POSITIVE, "user expressed agreement",
                0.7, Map.of());
        var signal = new MentalStateSignal.RelationshipCue(event);
        assertThat(signal.content()).isEqualTo("user expressed agreement");
        assertThat(signal.event()).isEqualTo(event);
    }

    @Test
    void verbalCueNullContentThrows() {
        assertThatThrownBy(() -> new MentalStateSignal.VerbalCue(null, CueType.BELIEF_STATEMENT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void verbalCueNullTypeThrows() {
        assertThatThrownBy(() -> new MentalStateSignal.VerbalCue("content", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void behavioralCueNullContentThrows() {
        assertThatThrownBy(() -> new MentalStateSignal.BehavioralCue(null, "type"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void relationshipCueNullEventThrows() {
        assertThatThrownBy(() -> new MentalStateSignal.RelationshipCue(null))
                .isInstanceOf(NullPointerException.class);
    }
}
