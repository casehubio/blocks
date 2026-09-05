package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.memory.mood.MoodState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoodPromptSectionTest {

    private static final PromptContext CTX = new PromptContext("agent1", "tenant1", null);

    @Test
    void rendersPadState() {
        var mood = mock(MoodOrchestrator.class);
        when(mood.currentMood("agent1", "tenant1")).thenReturn(Optional.of(
                new MoodState("agent1", "tenant1", Instant.now(), 0.5, -0.3, 0.2,
                        "recent interaction", null, Map.of())));
        var section = new MoodPromptSection(mood);
        var result = section.contribute(CTX);
        assertThat(result).isNotNull();
        assertThat(result).containsIgnoringCase("pleasure");
        assertThat(result).containsIgnoringCase("arousal");
        assertThat(result).containsIgnoringCase("dominance");
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
                new MoodState("agent1", "tenant1", Instant.now(), 0.7, 0.0, 0.0,
                        "good news", null, Map.of())));
        var section = new MoodPromptSection(mood);
        var result = section.contribute(CTX);
        assertThat(result).containsIgnoringCase("positive");
    }

    @Test
    void interpretsNegativePleasure() {
        var mood = mock(MoodOrchestrator.class);
        when(mood.currentMood("agent1", "tenant1")).thenReturn(Optional.of(
                new MoodState("agent1", "tenant1", Instant.now(), -0.6, 0.0, 0.0,
                        "bad news", null, Map.of())));
        var section = new MoodPromptSection(mood);
        var result = section.contribute(CTX);
        assertThat(result).containsIgnoringCase("negative");
    }
}
