package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.eidos.api.DispositionValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityPromptSectionTest {

    private static final PromptContext CTX = new PromptContext("a", "t", null);

    @Test
    void rendersDispositionProfile() {
        var values = List.of(
                new DispositionValue("warmth", 0.8),
                new DispositionValue("assertiveness", 0.6));
        var section = new PersonalityPromptSection(values);
        var result = section.contribute(CTX);
        assertThat(result).isNotNull();
        assertThat(result).contains("warmth");
        assertThat(result).contains("assertiveness");
        assertThat(result).contains("0.8");
    }

    @Test
    void returnsNullForEmptyProfile() {
        var section = new PersonalityPromptSection(List.of());
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void returnsNullForNullProfile() {
        var section = new PersonalityPromptSection(null);
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void ordersByWeightDescending() {
        var values = List.of(
                new DispositionValue("low", 0.2),
                new DispositionValue("high", 0.9),
                new DispositionValue("mid", 0.5));
        var section = new PersonalityPromptSection(values);
        var result = section.contribute(CTX);
        assertThat(result).isNotNull();
        int highIdx = result.indexOf("high");
        int midIdx = result.indexOf("mid");
        int lowIdx = result.indexOf("low");
        assertThat(highIdx).isLessThan(midIdx);
        assertThat(midIdx).isLessThan(lowIdx);
    }
}
