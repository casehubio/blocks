package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.agentic.social.BdiDimension;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MentalModelPromptSectionTest {

    @Test
    void rendersBdiForMatchingSubject() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        var now = Instant.now();
        var snapshot = new MentalModelSnapshot("a1", "user1", "t1",
                List.of(new AttributedState("tech", "User knows Java", 0.8, 1, now, BdiDimension.BELIEF)),
                List.of(new AttributedState("help", "Wants debugging help", 0.7, 1, now, BdiDimension.DESIRE)),
                List.of(),
                now, now, now);
        when(orchestrator.activeSnapshots("a1", "t1")).thenReturn(List.of(snapshot));
        var section = new MentalModelPromptSection(orchestrator);
        var result = section.contribute(new PromptContext("a1", "t1", "user1"));
        assertThat(result).isNotNull();
        assertThat(result).contains("User knows Java");
        assertThat(result).contains("Wants debugging help");
    }

    @Test
    void returnsNullWhenNoSnapshots() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("a1", "t1")).thenReturn(List.of());
        var section = new MentalModelPromptSection(orchestrator);
        assertThat(section.contribute(new PromptContext("a1", "t1", null))).isNull();
    }

    @Test
    void filtersLowConfidenceStates() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        var now = Instant.now();
        var snapshot = new MentalModelSnapshot("a1", "user1", "t1",
                List.of(new AttributedState("low", "Low confidence", 0.1, 1, now, BdiDimension.BELIEF)),
                List.of(), List.of(),
                now, now, now);
        when(orchestrator.activeSnapshots("a1", "t1")).thenReturn(List.of(snapshot));
        var section = new MentalModelPromptSection(orchestrator);
        var result = section.contribute(new PromptContext("a1", "t1", "user1"));
        assertThat(result).isNull();
    }
}
