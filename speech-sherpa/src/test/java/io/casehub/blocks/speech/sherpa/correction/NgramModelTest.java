package io.casehub.blocks.speech.sherpa.correction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NgramModelTest {

    private static NgramModel model;

    @BeforeAll
    static void loadModel() {
        model = NgramModel.fromResource("test-bigrams.txt");
    }

    @Test
    void ranksLimerickHigherThanUnknownInContextAfterA() {
        double scoreLimerick = model.score("a", "limerick", "");
        double scoreUnknown = model.score("a", "relimberate", "");
        assertThat(scoreLimerick).isGreaterThan(scoreUnknown);
    }

    @Test
    void ranksKnownBigramHigherThanUnknownBigram() {
        double knownPair = model.score("hello", "world", "");
        double unknownPair = model.score("hello", "xyzabc", "");
        assertThat(knownPair).isGreaterThan(unknownPair);
    }

    @Test
    void unknownBigramFallsBackToFiniteScore() {
        double score = model.score("xyz", "hello", "abc");
        assertThat(score).isFinite();
    }

    @Test
    void contextOnBothSidesBoostsScore() {
        double bothSides = model.score("read", "a", "limerick");
        double oneSide = model.score("read", "a", "xyzabc");
        assertThat(bothSides).isGreaterThanOrEqualTo(oneSide);
    }

    @Test
    void emptyPrevStillScores() {
        double score = model.score("", "hello", "world");
        assertThat(score).isFinite();
    }

    @Test
    void emptyNextStillScores() {
        double score = model.score("hello", "world", "");
        assertThat(score).isFinite();
    }
}
