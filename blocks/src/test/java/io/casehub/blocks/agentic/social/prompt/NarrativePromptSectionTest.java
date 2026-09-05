package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.IndividualEpisode;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrativePromptSectionTest {

    private static final PromptContext CTX = new PromptContext("agent1", "tenant1", null);

    @Test
    void rendersNarrativeState() {
        var narrative = mock(NarrativeOrchestrator.class);
        var now = Instant.now();
        var state = new NarrativeState("agent1", "tenant1", NarrativeScope.INDIVIDUAL,
                List.of(
                        new DerivedTheme("t1", now, null, List.of("identity"),
                                "helper", 0.8, java.util.Map.of(), List.of()),
                        new IndividualEpisode("e1", now, null, List.of("help"),
                                "Helped a user solve a problem", 0.6, List.of())),
                now, 0);
        when(narrative.currentNarrative("agent1", "tenant1")).thenReturn(Optional.of(state));
        var section = new NarrativePromptSection(narrative);
        var result = section.contribute(CTX);
        assertThat(result).isNotNull();
        assertThat(result).containsIgnoringCase("helper");
    }

    @Test
    void returnsNullWhenNoNarrative() {
        var narrative = mock(NarrativeOrchestrator.class);
        when(narrative.currentNarrative("agent1", "tenant1")).thenReturn(Optional.empty());
        var section = new NarrativePromptSection(narrative);
        assertThat(section.contribute(CTX)).isNull();
    }
}
