package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GectorFilterTest {

    @Test
    void name() {
        assertThat(GectorFilter.NAME).isEqualTo("grammar");
    }

    @Test
    void destructiveness() {
        assertThat(GectorFilter.DESTRUCTIVENESS).isEqualTo(3);
    }

    @Test
    void softmaxNormalizesToProbabilities() {
        float[] logits = {1.0f, 2.0f, 3.0f};
        float[] probs = GectorFilter.softmax(logits);
        float sum = 0;
        for (float p : probs) sum += p;
        assertThat(sum).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-5f));
        assertThat(probs[2]).isGreaterThan(probs[1]);
        assertThat(probs[1]).isGreaterThan(probs[0]);
    }

    @Test
    void softmaxHandlesLargeValues() {
        float[] logits = {1000f, 1001f, 1002f};
        float[] probs = GectorFilter.softmax(logits);
        float sum = 0;
        for (float p : probs) sum += p;
        assertThat(sum).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-5f));
    }

    @Test
    void aggregateSubwordTagsFirstWins() {
        int[] subwordTags = {3, 5, 5, 7, 7, 7};
        int[] wordBoundaries = {0, 3};
        int[] wordTags = GectorFilter.aggregateSubwordTags(subwordTags, wordBoundaries, 2);
        assertThat(wordTags).containsExactly(3, 7);
    }

    @Test
    void aggregateSubwordTagsSingleSubword() {
        int[] subwordTags = {4, 6};
        int[] wordBoundaries = {0, 1};
        int[] wordTags = GectorFilter.aggregateSubwordTags(subwordTags, wordBoundaries, 2);
        assertThat(wordTags).containsExactly(4, 6);
    }

    @Test
    void buildWordBoundariesFromSpaceSymbol() {
        int[] ids = {10, 20, 30, 40, 50};
        String[] pieces = {"▁He", "ll", "o", "▁world", "s"};
        int[] boundaries = GectorFilter.buildWordBoundaries(ids, pieces);
        assertThat(boundaries).containsExactly(0, 3);
    }
}
