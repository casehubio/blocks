package io.casehub.blocks.speech.sherpa.correction;

import io.casehub.blocks.speech.CorrectionStrategy;
import io.casehub.blocks.speech.CorrectionStrategy.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptCorrectorTest {

    private static final Set<String> DICTIONARY = Set.of(
            "the", "a", "an", "is", "was", "read", "hello", "world", "cat", "limerick");

    @Test
    void correctsUnknownWordUsingStrategy() {
        CorrectionStrategy strategy = (word, ctx) ->
                word.equals("relimberate")
                        ? List.of(new Candidate("limerick", 0.8, "test"))
                        : List.of();

        var corrector = new TranscriptCorrector(List.of(strategy), null, DICTIONARY);
        assertThat(corrector.correct("read a relimberate")).isEqualTo("read a limerick");
    }

    @Test
    void leavesKnownWordsUnchanged() {
        var corrector = new TranscriptCorrector(List.of(), null, DICTIONARY);
        assertThat(corrector.correct("hello world")).isEqualTo("hello world");
    }

    @Test
    void addVocabularyExpandsDictionary() {
        var corrector = new TranscriptCorrector(List.of(), null, Set.of("hello"));
        corrector.addVocabulary("newterm");
        assertThat(corrector.correct("newterm")).isEqualTo("newterm");
    }

    @Test
    void emptyInputReturnsEmpty() {
        var corrector = new TranscriptCorrector(List.of(), null, DICTIONARY);
        assertThat(corrector.correct("")).isEqualTo("");
    }

    @Test
    void singleUnknownWordWithNoStrategyLeftUnchanged() {
        var corrector = new TranscriptCorrector(List.of(), null, DICTIONARY);
        assertThat(corrector.correct("xyzabc")).isEqualTo("xyzabc");
    }

    @Test
    void highestConfidenceWinsWithoutNgramModel() {
        CorrectionStrategy strategy = (word, ctx) -> List.of(
                new Candidate("cat", 0.5, "low"),
                new Candidate("limerick", 0.9, "high"),
                new Candidate("hello", 0.3, "lowest"));

        var corrector = new TranscriptCorrector(List.of(strategy), null, DICTIONARY);
        assertThat(corrector.correct("xyzabc")).isEqualTo("limerick");
    }

    @Test
    void multipleStrategiesMergeCandidates() {
        CorrectionStrategy s1 = (word, ctx) -> List.of(new Candidate("cat", 0.6, "s1"));
        CorrectionStrategy s2 = (word, ctx) -> List.of(new Candidate("limerick", 0.9, "s2"));

        var corrector = new TranscriptCorrector(List.of(s1, s2), null, DICTIONARY);
        assertThat(corrector.correct("xyzabc")).isEqualTo("limerick");
    }

    @Test
    void strategyExceptionDoesNotBreakPipeline() {
        CorrectionStrategy failing = (word, ctx) -> { throw new RuntimeException("boom"); };
        CorrectionStrategy working = (word, ctx) ->
                List.of(new Candidate("hello", 0.8, "ok"));

        var corrector = new TranscriptCorrector(List.of(failing, working), null, DICTIONARY);
        assertThat(corrector.correct("xyzabc")).isEqualTo("hello");
    }

    @Test
    void ngramModelUsedForRankingWhenPresent() {
        CorrectionStrategy strategy = (word, ctx) ->
                word.equals("xyzabc")
                        ? List.of(new Candidate("cat", 0.9, "high-conf"),
                                  new Candidate("limerick", 0.3, "low-conf"))
                        : List.of();

        var ngram = new NgramModel() {
            @Override
            public double score(String prev, String candidate, String next) {
                return candidate.equals("limerick") ? 10.0 : 1.0;
            }
        };

        var corrector = new TranscriptCorrector(List.of(strategy), ngram, DICTIONARY);
        assertThat(corrector.correct("read a xyzabc")).isEqualTo("read a limerick");
    }

    @Test
    void dictionaryLookupIsCaseInsensitive() {
        var corrector = new TranscriptCorrector(List.of(), null, Set.of("hello"));
        assertThat(corrector.correct("Hello")).isEqualTo("Hello");
    }

    @Test
    void contextPassesPreviousAndNextWords() {
        CorrectionStrategy strategy = (word, ctx) -> {
            if (word.equals("xyzabc")) {
                assertThat(ctx.previousWord()).isEqualTo("read");
                assertThat(ctx.nextWord()).isEqualTo("world");
            }
            return List.of();
        };

        var corrector = new TranscriptCorrector(List.of(strategy), null, DICTIONARY);
        corrector.correct("read xyzabc world");
    }

    @Test
    void contextAtStartHasEmptyPrevious() {
        CorrectionStrategy strategy = (word, ctx) -> {
            assertThat(ctx.previousWord()).isEmpty();
            return List.of(new Candidate("hello", 0.8, "test"));
        };

        var corrector = new TranscriptCorrector(List.of(strategy), null, DICTIONARY);
        corrector.correct("xyzabc world");
    }

    @Test
    void contextAtEndHasEmptyNext() {
        CorrectionStrategy strategy = (word, ctx) -> {
            assertThat(ctx.nextWord()).isEmpty();
            return List.of(new Candidate("world", 0.8, "test"));
        };

        var corrector = new TranscriptCorrector(List.of(strategy), null, DICTIONARY);
        corrector.correct("hello xyzabc");
    }

    @Test
    void addVocabularyPreventsCorrection() {
        CorrectionStrategy strategy = (word, ctx) ->
                List.of(new Candidate("hello", 0.9, "test"));

        var corrector = new TranscriptCorrector(List.of(strategy), null, Set.of("hello"));
        corrector.addVocabulary("kubernetes");
        assertThat(corrector.correct("kubernetes")).isEqualTo("kubernetes");
    }

    @Test
    void multipleUnknownWordsCorrectedIndependently() {
        CorrectionStrategy strategy = (word, ctx) -> switch (word) {
            case "helo" -> List.of(new Candidate("hello", 0.9, "test"));
            case "wrld" -> List.of(new Candidate("world", 0.9, "test"));
            default -> List.of();
        };

        var corrector = new TranscriptCorrector(List.of(strategy), null, DICTIONARY);
        assertThat(corrector.correct("helo wrld")).isEqualTo("hello world");
    }

    @Test
    void conversationVocabularyPassedInContext() {
        CorrectionStrategy strategy = (word, ctx) -> {
            assertThat(ctx.conversationVocabulary()).contains("hello", "world");
            return List.of();
        };

        var corrector = new TranscriptCorrector(List.of(strategy), null, Set.of("hello", "world"));
        corrector.correct("xyzabc");
    }

// --- Contextual correction of known words ---

    @Test
    void knownWordReplacedWhenNgramStronglyPrefersAlternative() {
        // "limit" is a known word, but "a limerick" is a much stronger bigram than "a limit"
        CorrectionStrategy strategy = (word, ctx) ->
                                              word.equals("limit")
                                              ? List.of(new Candidate("limerick", 0.6, "phonetic"))
                                              : List.of();

        var ngram = new NgramModel() {
            @Override
            public double score(String prev, String candidate, String next) {
                if (prev.equals("a") && candidate.equals("limerick")) {return 10.0;}
                if (prev.equals("a") && candidate.equals("limit")) {return 1.0;}
                return 0.0;
            }
        };

        var corrector = new TranscriptCorrector(List.of(strategy), ngram,
                                                Set.of("a", "limit", "limerick", "read"));
        assertThat(corrector.correct("read a limit")).isEqualTo("read a limerick");
    }

    @Test
    void knownWordKeptWhenNgramDoesNotStronglyPreferAlternative() {
        // "cat" is a known word, strategies propose "cat" → "car" but n-gram scores are similar
        CorrectionStrategy strategy = (word, ctx) ->
                                              word.equals("cat")
                                              ? List.of(new Candidate("car", 0.6, "test"))
                                              : List.of();

        var ngram = new NgramModel() {
            @Override
            public double score(String prev, String candidate, String next) {
                if (candidate.equals("cat")) {return 5.0;}
                if (candidate.equals("car")) {return 5.5;}
                return 0.0;
            }
        };

        var corrector = new TranscriptCorrector(List.of(strategy), ngram,
                                                Set.of("the", "cat", "car"));
        assertThat(corrector.correct("the cat")).isEqualTo("the cat");
    }

    @Test
    void knownWordKeptWhenNoNgramModel() {
        CorrectionStrategy strategy = (word, ctx) ->
                                              List.of(new Candidate("world", 0.9, "test"));

        var corrector = new TranscriptCorrector(List.of(strategy), null,
                                                Set.of("hello", "world"));
        assertThat(corrector.correct("hello")).isEqualTo("hello");
    }
}
