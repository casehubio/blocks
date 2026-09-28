package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.memory.mood.MoodState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoodPromptSectionTest {

    private static final PromptContext CTX = new PromptContext("agent1", "tenant1", null);

    @Test
    void rendersPadState() {
        var mood = mock(MoodOrchestrator.class);
        when(mood.currentMood("agent1", "tenant1")).thenReturn(Optional.of(
                new MoodState("agent1", "tenant1", Instant.now(), 0.5, 0.4, 0.6,
                              "recent success", null, Set.of(), Map.of())));
        var section = new MoodPromptSection(mood);
        var result  = section.contribute(CTX);
        assertThat(result).isNotNull();
        // Should use emotional labels, not raw PAD numbers
        assertThat(result).containsIgnoringCase("excited");
        assertThat(result).containsIgnoringCase("confidence");
        assertThat(result).contains("colors your responses");
        // Should NOT contain raw numeric values
        assertThat(result).doesNotContain("0.50");
        assertThat(result).doesNotContain("0.40");
        assertThat(result).doesNotContain("0.60");
    }

    @Test
    void returnsNullWhenNoMoodState() {
        var mood = mock(MoodOrchestrator.class);
        when(mood.currentMood("agent1", "tenant1")).thenReturn(Optional.empty());
        var section = new MoodPromptSection(mood);
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void interpretsPositivePleasure() {
        var mood = mock(MoodOrchestrator.class);
        when(mood.currentMood("agent1", "tenant1")).thenReturn(Optional.of(
                new MoodState("agent1", "tenant1", Instant.now(), 0.7, -0.4, 0.0,
                              "good news", null, Set.of(), Map.of())));
        var section = new MoodPromptSection(mood);
        var result  = section.contribute(CTX);
        assertThat(result).containsIgnoringCase("content");
        assertThat(result).containsIgnoringCase("generous");
    }

    @Test
    void interpretsNegativePleasure() {
        var mood = mock(MoodOrchestrator.class);
        when(mood.currentMood("agent1", "tenant1")).thenReturn(Optional.of(
                new MoodState("agent1", "tenant1", Instant.now(), -0.6, 0.5, -0.4,
                              "bad news", null, Set.of(), Map.of())));
        var section = new MoodPromptSection(mood);
        var result  = section.contribute(CTX);
        assertThat(result).containsIgnoringCase("tense");
        assertThat(result).containsIgnoringCase("guarded");
        assertThat(result).containsIgnoringCase("hesitant");
    }
}
