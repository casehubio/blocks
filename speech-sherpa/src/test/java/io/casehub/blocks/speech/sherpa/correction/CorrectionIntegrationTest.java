package io.casehub.blocks.speech.sherpa.correction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectionIntegrationTest {

    private static TranscriptCorrector corrector;

    @BeforeAll
    static void buildPipeline() {
        var symSpell = SymSpellIndex.fromResource("test-dictionary.txt");
        var phonetic = PhoneticIndex.fromWords(
                List.of("limerick", "hello", "world", "cat", "the", "a", "read"));
        var ngram = NgramModel.fromResource("test-bigrams.txt");

        corrector = new TranscriptCorrector(
                List.of(new SymSpellStrategy(symSpell), new PhoneticStrategy(phonetic)),
                ngram,
                Set.of("the", "a", "read", "hello", "world", "cat", "limerick"));
    }

    @Test
    void correctsHelloTypo() {
        assertThat(corrector.correct("helo world")).isEqualTo("hello world");
    }

    @Test
    void leavesCorrectSentenceUnchanged() {
        assertThat(corrector.correct("hello world")).isEqualTo("hello world");
    }

    @Test
    void correctsMultipleErrors() {
        assertThat(corrector.correct("helo wrld")).isEqualTo("hello world");
    }

    @Test
    void ngramContextRanksLimerickOverCat() {
        // "a limerick" is a known bigram; "a cat" is also known but
        // the SymSpell candidate for a 1-edit typo of limerick should
        // pick limerick when the context favours it
        assertThat(corrector.correct("read a limmerick")).isEqualTo("read a limerick");
    }

    @Test
    void dynamicVocabularyPreventsCorrection() {
        var symSpell = SymSpellIndex.fromResource("test-dictionary.txt");
        var phonetic = PhoneticIndex.fromWords(List.of("hello"));
        var local = new TranscriptCorrector(
                List.of(new SymSpellStrategy(symSpell), new PhoneticStrategy(phonetic)),
                null,
                Set.of("hello"));
        local.addVocabulary("kubernetes");
        assertThat(local.correct("kubernetes")).isEqualTo("kubernetes");
    }
}
