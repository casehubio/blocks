package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordTokenizerTest {

    @Test
    void simpleWords() {
        assertThat(WordTokenizer.tokenize("hello world"))
                .containsExactly("hello", "world");
    }

    @Test
    void contractionNot() {
        assertThat(WordTokenizer.tokenize("don't"))
                .containsExactly("do", "n't");
    }

    @Test
    void contractionWould() {
        assertThat(WordTokenizer.tokenize("I'd go"))
                .containsExactly("I", "'d", "go");
    }

    @Test
    void contractionIs() {
        assertThat(WordTokenizer.tokenize("it's"))
                .containsExactly("it", "'s");
    }

    @Test
    void contractionAre() {
        assertThat(WordTokenizer.tokenize("they're"))
                .containsExactly("they", "'re");
    }

    @Test
    void contractionHave() {
        assertThat(WordTokenizer.tokenize("I've"))
                .containsExactly("I", "'ve");
    }

    @Test
    void contractionWill() {
        assertThat(WordTokenizer.tokenize("she'll"))
                .containsExactly("she", "'ll");
    }

    @Test
    void contractionAm() {
        assertThat(WordTokenizer.tokenize("I'm"))
                .containsExactly("I", "'m");
    }

    @Test
    void possessive() {
        assertThat(WordTokenizer.tokenize("cat's"))
                .containsExactly("cat", "'s");
    }

    @Test
    void punctuationAtEnd() {
        assertThat(WordTokenizer.tokenize("hello,"))
                .containsExactly("hello", ",");
    }

    @Test
    void punctuationPeriod() {
        assertThat(WordTokenizer.tokenize("hello world."))
                .containsExactly("hello", "world", ".");
    }

    @Test
    void multipleSpaces() {
        assertThat(WordTokenizer.tokenize("hello   world"))
                .containsExactly("hello", "world");
    }

    @Test
    void emptyInput() {
        assertThat(WordTokenizer.tokenize("")).isEmpty();
    }

    @Test
    void sentenceSplit() {
        assertThat(WordTokenizer.splitSentences("Hello. World!"))
                .containsExactly("Hello.", "World!");
    }

    @Test
    void sentenceSplitSingle() {
        assertThat(WordTokenizer.splitSentences("Hello world"))
                .containsExactly("Hello world");
    }

    @Test
    void sentenceSplitEmpty() {
        assertThat(WordTokenizer.splitSentences("")).isEmpty();
    }

    @Test
    void questionMark() {
        assertThat(WordTokenizer.tokenize("Really?"))
                .containsExactly("Really", "?");
    }

    @Test
    void exclamation() {
        assertThat(WordTokenizer.tokenize("Wow!"))
                .containsExactly("Wow", "!");
    }

    @Test
    void semicolonAndColon() {
        assertThat(WordTokenizer.tokenize("yes; no: maybe"))
                .containsExactly("yes", ";", "no", ":", "maybe");
    }

    @Test
    void parentheses() {
        assertThat(WordTokenizer.tokenize("(hello)"))
                .containsExactly("(", "hello", ")");
    }

    @Test
    void mixedContractionAndPunctuation() {
        assertThat(WordTokenizer.tokenize("I can't go."))
                .containsExactly("I", "ca", "n't", "go", ".");
    }
}
