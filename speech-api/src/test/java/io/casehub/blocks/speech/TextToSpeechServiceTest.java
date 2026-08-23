package io.casehub.blocks.speech;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TextToSpeechServiceTest {

    @Test
    void synthesisResultRejectsNullAudioData() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SynthesisResult(null, "wav", List.of()));
    }

    @Test
    void synthesisResultRejectsNullFormat() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SynthesisResult(new byte[]{1}, null, List.of()));
    }

    @Test
    void synthesisResultDefensiveCopiesPhonemes() {
        final var phonemes = new java.util.ArrayList<>(List.of(
                new PhonemeTiming("ah", 0, 100)));
        final var result = new SynthesisResult(new byte[]{1}, "wav", phonemes);
        phonemes.clear();
        assertThat(result.phonemes()).hasSize(1);
    }

    @Test
    void synthesisResultNullPhonemesBecomesEmptyList() {
        final var result = new SynthesisResult(new byte[]{1}, "wav", null);
        assertThat(result.phonemes()).isEmpty();
    }

    @Test
    void phonemeTimingRejectsNullPhoneme() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PhonemeTiming(null, 0, 100));
    }

    @Test
    void phonemeTimingRejectsInvalidRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PhonemeTiming("ah", 100, 50));
    }

    @Test
    void phonemeTimingAcceptsEqualStartEnd() {
        final var p = new PhonemeTiming("ah", 100, 100);
        assertThat(p.startMs()).isEqualTo(100);
        assertThat(p.endMs()).isEqualTo(100);
    }

    @Test
    void synthesisOptionsDefaultsReturnsWavNoPhonemes() {
        final var opts = SynthesisOptions.defaults();
        assertThat(opts.audioFormat()).isEqualTo("wav");
        assertThat(opts.includePhonemes()).isFalse();
        assertThat(opts.voice()).isNull();
        assertThat(opts.language()).isNull();
    }

    @Test
    void stubImplementationSynthesisesText() {
        final TextToSpeechService tts = (text, options) ->
                new SynthesisResult(text.getBytes(), "wav", List.of());

        final var result = tts.synthesise("hello", SynthesisOptions.defaults());
        assertThat(result.audioData()).isEqualTo("hello".getBytes());
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.phonemes()).isEmpty();
    }
}
