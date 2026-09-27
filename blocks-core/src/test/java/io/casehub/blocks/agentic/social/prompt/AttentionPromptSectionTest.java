package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttentionPromptSectionTest {

    @Test
    void rendersSignalsBySignificance() {
        var briefing = new AttentionBriefing("agent", "tenant",
            List.of(
                new AttentionSignal("agent", "tenant", SignalCategory.URGENCY_SPIKE,
                    "n1", "Project deadline", 0.92, "urgency rose past threshold"),
                new AttentionSignal("agent", "tenant", SignalCategory.DECAY_DETECTED,
                    "n2", "Learn Spanish", 0.5, "no activity for 14 days")),
            0.85, Instant.now());

        var section = new AttentionPromptSection(briefing);
        var result = section.contribute(new PromptContext("agent", "tenant", null));

        assertThat(result).isNotNull();
        assertThat(result).contains("Attention required:");
        assertThat(result).contains("[URGENCY_SPIKE] Project deadline");
        assertThat(result).contains("0.92");
        assertThat(result).contains("urgency rose past threshold");
        assertThat(result).contains("[DECAY_DETECTED] Learn Spanish");
        assertThat(result).contains("no activity for 14 days");
    }

    @Test
    void returnsNullForEmptySignals() {
        var briefing = new AttentionBriefing("agent", "tenant",
            List.of(), 0.0, Instant.now());
        var section = new AttentionPromptSection(briefing);
        assertThat(section.contribute(new PromptContext("agent", "tenant", null))).isNull();
    }
}
