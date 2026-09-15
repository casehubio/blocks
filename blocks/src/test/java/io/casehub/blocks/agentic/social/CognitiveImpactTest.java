package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveImpactTest {

    @Test
    void fromTextCreatesUserModelSignalOnly() {
        var impact = CognitiveImpact.fromText("greeted warmly");
        assertThat(impact.userModelSignal()).isNotNull();
        assertThat(impact.userModelSignal().description()).isEqualTo("greeted warmly");
        assertThat(impact.userModelSignal().quality()).isEqualTo(QualitySignal.NEUTRAL);
        assertThat(impact.moodSignal()).isNull();
        assertThat(impact.suppressBdiExtraction()).isFalse();
        assertThat(impact.strategySignal()).isNull();
    }

    @Test
    void allFieldsNullIsValid() {
        var impact = new CognitiveImpact(null, null, false, null, null);
        assertThat(impact.userModelSignal()).isNull();
        assertThat(impact.moodSignal()).isNull();
        assertThat(impact.suppressBdiExtraction()).isFalse();
        assertThat(impact.strategySignal()).isNull();
        assertThat(impact.conversationId()).isNull();
    }

    @Test
    void allFieldsPopulated() {
        var interaction = new InteractionSignal.CustomSignal("stole item", QualitySignal.NEGATIVE);
        var mood = new MoodSignal.DirectShift(-0.3, 0.2, -0.1, "theft");
        var impact = new CognitiveImpact(interaction, mood, true, null, null);
        assertThat(impact.userModelSignal()).isEqualTo(interaction);
        assertThat(impact.moodSignal()).isEqualTo(mood);
        assertThat(impact.suppressBdiExtraction()).isTrue();
        assertThat(impact.strategySignal()).isNull();
        assertThat(impact.conversationId()).isNull();
    }

    @Test
    void conversationIdFieldIsAccessible() {
        var impact = new CognitiveImpact(null, null, false, null, "conv-123");
        assertThat(impact.conversationId()).isEqualTo("conv-123");
    }

    @Test
    void fromTextPreservesNullConversationId() {
        var impact = CognitiveImpact.fromText("hello");
        assertThat(impact.conversationId()).isNull();
    }

    @Test
    void withConversationIdFactory() {
        var impact = CognitiveImpact.withConversationId("conv-456");
        assertThat(impact.conversationId()).isEqualTo("conv-456");
        assertThat(impact.userModelSignal()).isNull();
        assertThat(impact.suppressBdiExtraction()).isFalse();
    }
}
