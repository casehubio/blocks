package io.casehub.blocks.speech;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LipSyncEnricherTest {

    private final PhonemeAligner stubAligner = (text, audio, rate) ->
            List.of(new PhonemeTiming("ah", 0, 100), new PhonemeTiming("l", 100, 200));

    @Test
    void enrichesWhenDelegateReturnsEmptyPhonemes() {
        TextToSpeechService delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", List.of());

        var enriched = LipSyncEnricher.wrap(delegate, stubAligner);
        var result = enriched.synthesise("hello", SynthesisOptions.defaults());

        assertThat(result.audioData()).containsExactly(1, 2, 3);
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.phonemes()).hasSize(2);
        assertThat(result.phonemes().getFirst().phoneme()).isEqualTo("ah");
    }

    @Test
    void passesThroughWhenDelegateReturnsNonEmptyPhonemes() {
        var nativePhonemes = List.of(new PhonemeTiming("n", 0, 50));
        TextToSpeechService delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1}, "wav", nativePhonemes);

        var enriched = LipSyncEnricher.wrap(delegate, stubAligner);
        var result = enriched.synthesise("hi", SynthesisOptions.defaults());

        assertThat(result.phonemes()).hasSize(1);
        assertThat(result.phonemes().getFirst().phoneme()).isEqualTo("n");
    }

    @Test
    void passesThroughTextAndOptionsToDelegate() {
        final String[] capturedText = {null};
        final SynthesisOptions[] capturedOpts = {null};
        TextToSpeechService delegate = (text, opts) -> {
            capturedText[0] = text;
            capturedOpts[0] = opts;
            return new SynthesisResult(new byte[]{1}, "wav", List.of());
        };

        var opts = new SynthesisOptions("voice1", "en", "wav", true);
        LipSyncEnricher.wrap(delegate, stubAligner).synthesise("test", opts);

        assertThat(capturedText[0]).isEqualTo("test");
        assertThat(capturedOpts[0]).isSameAs(opts);
    }

    @Test
    void enrichesWhenDelegateReturnsNullPhonemes() {
        TextToSpeechService delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1}, "wav", null);

        var enriched = LipSyncEnricher.wrap(delegate, stubAligner);
        var result = enriched.synthesise("hello", SynthesisOptions.defaults());

        assertThat(result.phonemes()).hasSize(2);
    }

    @Test
    void customSampleRatePassedToAligner() {
        final int[] capturedRate = {0};
        PhonemeAligner rateCapture = (text, audio, rate) -> {
            capturedRate[0] = rate;
            return List.of();
        };

        TextToSpeechService delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1}, "wav", List.of());

        LipSyncEnricher.wrap(delegate, rateCapture, 44100).synthesise("hi", SynthesisOptions.defaults());

        assertThat(capturedRate[0]).isEqualTo(44100);
    }
}
