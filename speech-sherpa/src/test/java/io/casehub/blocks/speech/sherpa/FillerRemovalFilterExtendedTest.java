package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FillerRemovalFilterExtendedTest {

    private final FillerRemovalFilter filter = new FillerRemovalFilter();

    // --- Discourse markers ---

    @Test
    void removesYouKnow() {
        assertThat(filter.apply("it was you know really good"))
                .isEqualTo("it was really good");
    }

    @Test
    void removesIMeanAtStart() {
        assertThat(filter.apply("I mean the thing is"))
                .isEqualTo("the thing is");
    }

    @Test
    void removesBasically() {
        assertThat(filter.apply("it basically works"))
                .isEqualTo("it works");
    }

    @Test
    void removesActually() {
        assertThat(filter.apply("I actually think so"))
                .isEqualTo("I think so");
    }

    @Test
    void removesLiterallyAsFiller() {
        assertThat(filter.apply("it was literally amazing"))
                .isEqualTo("it was amazing");
    }

    // --- False starts / repeated words ---

    @Test
    void removesRepeatedWords() {
        assertThat(filter.apply("I I went to the store"))
                .isEqualTo("I went to the store");
    }

    @Test
    void removesMultipleRepeatedWords() {
        assertThat(filter.apply("the the the cat sat"))
                .isEqualTo("the cat sat");
    }

    // --- "like" as filler ---

    @Test
    void removesLikeAfterWas() {
        assertThat(filter.apply("I was like walking"))
                .isEqualTo("I was walking");
    }

    @Test
    void preservesLikeAsVerb() {
        assertThat(filter.apply("I like cats"))
                .isEqualTo("I like cats");
    }

    // --- Existing patterns still work ---

    @Test
    void stillRemovesBasicFillers() {
        assertThat(filter.apply("um the uh yellow lamps"))
                .isEqualTo("the yellow lamps");
    }

    @Test
    void preservesRealWords() {
        assertThat(filter.apply("the umbrella is here"))
                .isEqualTo("the umbrella is here");
    }

    @Test
    void handlesMixedFillersAndDiscourseMarkers() {
        assertThat(filter.apply("um you know I basically uh went"))
                .isEqualTo("I went");
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(filter.apply("")).isEmpty();
    }

    @Test
    void destructivenessIs1() {
        assertThat(filter.destructiveness()).isEqualTo(1);
    }
}
