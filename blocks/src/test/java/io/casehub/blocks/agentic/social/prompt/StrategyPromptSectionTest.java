package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.StrategyProfile;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyPromptSectionTest {

    private static final PromptContext CTX = new PromptContext("a1", "t1", null);

    @Test
    void rendersStrategyGuidelines() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        var profile = new StrategyProfile("a1", "t1",
                Map.of("empathy", 0.8, "directness", 0.6),
                List.of("Be empathetic and patient", "Use concrete examples"),
                Instant.now(), 10);
        when(orchestrator.currentStrategy("a1", "t1")).thenReturn(Optional.of(profile));
        var section = new StrategyPromptSection(orchestrator);
        var result = section.contribute(CTX);
        assertThat(result).isNotNull();
        assertThat(result).contains("Be empathetic and patient");
        assertThat(result).contains("Use concrete examples");
    }

    @Test
    void returnsNullWhenNoStrategy() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.currentStrategy("a1", "t1")).thenReturn(Optional.empty());
        var section = new StrategyPromptSection(orchestrator);
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void returnsNullWhenEmptyGuidelines() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        var profile = new StrategyProfile("a1", "t1",
                Map.of(), List.of(), Instant.now(), 0);
        when(orchestrator.currentStrategy("a1", "t1")).thenReturn(Optional.of(profile));
        var section = new StrategyPromptSection(orchestrator);
        assertThat(section.contribute(CTX)).isNull();
    }
}
