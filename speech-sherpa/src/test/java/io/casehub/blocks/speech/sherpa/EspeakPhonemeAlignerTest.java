package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.PhonemeAligner;
import io.casehub.blocks.speech.PhonemeTiming;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EspeakPhonemeAlignerTest {

    @Test
    void alignsPhonemeTimingsProportionallyToAudioDuration() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> "hɛloʊ");

        byte[] wavData = WavWriter.encode(new float[22050], 22050, 1); // 1 second of silence
        List<PhonemeTiming> result = aligner.align("hello", wavData, 22050);

        assertThat(result).hasSize(5);
        assertThat(result.getFirst().startMs()).isZero();
        assertThat(result.getLast().endMs()).isEqualTo(1000);
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).startMs()).isEqualTo(result.get(i - 1).endMs());
        }
    }

    @Test
    void phonemesContainCorrectIpaCharacters() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> "hɛloʊ");

        byte[] wavData = WavWriter.encode(new float[22050], 22050, 1);
        List<PhonemeTiming> result = aligner.align("hello", wavData, 22050);

        assertThat(result.stream().map(PhonemeTiming::phoneme).toList())
                .containsExactly("h", "ɛ", "l", "o", "ʊ");
    }

    @Test
    void handlesMultiWordWithSpaceSeparation() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> "hɛl oʊ");

        byte[] wavData = WavWriter.encode(new float[22050], 22050, 1);
        List<PhonemeTiming> result = aligner.align("hel lo", wavData, 22050);

        assertThat(result).hasSize(5);
        assertThat(result.getFirst().startMs()).isZero();
        assertThat(result.getLast().endMs()).isEqualTo(1000);
    }

    @Test
    void returnsEmptyListForEmptyText() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> "");
        List<PhonemeTiming> result = aligner.align("", new byte[0], 22050);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListForNullPhonemeOutput() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> null);
        byte[] wavData = WavWriter.encode(new float[11025], 22050, 1);
        List<PhonemeTiming> result = aligner.align("hi", wavData, 22050);
        assertThat(result).isEmpty();
    }

    @Test
    void handlesShortAudioGracefully() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> "hɛ");

        byte[] wavData = WavWriter.encode(new float[221], 22050, 1); // ~10ms
        List<PhonemeTiming> result = aligner.align("he", wavData, 22050);

        assertThat(result).hasSize(2);
        assertThat(result.getLast().endMs()).isEqualTo(10);
    }
}
