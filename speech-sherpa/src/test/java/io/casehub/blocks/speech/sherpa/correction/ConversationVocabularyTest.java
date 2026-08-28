package io.casehub.blocks.speech.sherpa.correction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationVocabularyTest {

    @Test
    void extractsTermsFromText() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("There once was a limerick from Nantucket");
        assertThat(vocab.terms()).contains("limerick", "nantucket");
        assertThat(vocab.terms()).doesNotContain("was", "once");
    }

    @Test
    void filtersShortWords() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("I am a big cat");
        assertThat(vocab.terms()).doesNotContain("i", "am", "a", "big", "cat");
    }

    @Test
    void keepsWordsOfLength5OrMore() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("hello world kubernetes");
        assertThat(vocab.terms()).contains("hello", "world", "kubernetes");
    }

    @Test
    void promptHintJoinsTerms() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("Tell me about limericks and sonnets");
        String hint = vocab.asPromptHint();
        assertThat(hint).contains("limericks");
        assertThat(hint).contains("sonnets");
    }

    @Test
    void emptyVocabularyReturnsEmptyHint() {
        var vocab = new ConversationVocabulary();
        assertThat(vocab.asPromptHint()).isEmpty();
    }

    @Test
    void emptyTerms() {
        var vocab = new ConversationVocabulary();
        assertThat(vocab.terms()).isEmpty();
    }

    @Test
    void deduplicatesTerms() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("hello hello hello world");
        assertThat(vocab.terms().stream().filter(t -> t.equals("hello")).count()).isEqualTo(1);
    }

    @Test
    void caseInsensitive() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("Hello HELLO hello");
        assertThat(vocab.terms()).contains("hello");
        assertThat(vocab.terms().stream().filter(t -> t.equals("hello")).count()).isEqualTo(1);
    }

    @Test
    void multipleAddFromTextAccumulates() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("Tell me about limericks");
        vocab.addFromText("And also about sonnets");
        assertThat(vocab.terms()).contains("limericks", "sonnets");
    }

    @Test
    void termsIsUnmodifiable() {
        var vocab = new ConversationVocabulary();
        vocab.addFromText("hello world kubernetes");
        assertThat(vocab.terms()).isUnmodifiable();
    }
}
