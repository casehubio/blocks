package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionPromptSectionTest {

    @Test
    void rendersInsightText() {
        var entries = List.of(
                entry("When facing resistance, try indirect approaches"),
                entry("Users respond better to questions than directives"));
        var section = new ReflectionPromptSection(entries);

        var result = section.contribute(new PromptContext("a", "t", null));

        assertThat(result).startsWith("Learned heuristics:");
        assertThat(result).contains("When facing resistance, try indirect approaches");
        assertThat(result).contains("Users respond better to questions than directives");
    }

    @Test
    void returnsNullForEmptyList() {
        var section = new ReflectionPromptSection(List.of());
        assertThat(section.contribute(new PromptContext("a", "t", null))).isNull();
    }

    @Test
    void limitsToFiveEntries() {
        var entries = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> entry("insight-" + i))
                .toList();
        var section = new ReflectionPromptSection(entries);

        var result = section.contribute(new PromptContext("a", "t", null));

        assertThat(result).contains("insight-1").contains("insight-5");
        assertThat(result).doesNotContain("insight-6");
    }

    private static ReflectionEntry entry(String insight) {
        return new ReflectionEntry("agent", "tenant", insight,
                Instant.now(), List.of());
    }
}
