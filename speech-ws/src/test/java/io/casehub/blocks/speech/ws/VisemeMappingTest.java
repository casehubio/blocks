package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.ws.protocol.VisemeFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisemeMappingTest {

    @ParameterizedTest
    @CsvSource({
            "p, PP", "b, PP", "m, PP",
            "f, FF", "v, FF",
            "θ, TH", "ð, TH",
            "t, DD", "d, DD", "l, DD",
            "k, kk", "g, kk", "ŋ, kk",
            "s, SS", "z, SS",
            "ʃ, CH", "ʒ, CH", "tʃ, CH", "dʒ, CH",
            "ɹ, RR", "r, RR",
            "n, nn",
            "ɑ, aa", "æ, aa", "ʌ, aa", "a, aa",
            "ɛ, E", "e, E", "ə, E",
            "ɪ, I", "i, I",
            "ɔ, O", "o, O", "ɒ, O",
            "ʊ, U", "u, U", "w, U"
    })
    void mapsIpaToOculusViseme(String ipa, String expectedViseme) {
        assertThat(VisemeMapping.toViseme(ipa)).isEqualTo(expectedViseme);
    }

    @Test
    void unknownPhonemeMapsToSilence() {
        assertThat(VisemeMapping.toViseme("ʔ")).isEqualTo("sil");
        assertThat(VisemeMapping.toViseme("")).isEqualTo("sil");
        assertThat(VisemeMapping.toViseme("xyz")).isEqualTo("sil");
    }

    @Test
    void nullPhonemeMapsToSilence() {
        assertThat(VisemeMapping.toViseme(null)).isEqualTo("sil");
    }

    @Test
    void convertsPhonemesTimingToVisemeFrames() {
        var phonemes = List.of(
                new PhonemeTiming("ð", 0, 100),
                new PhonemeTiming("ə", 100, 200),
                new PhonemeTiming("k", 200, 300));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames).extracting(VisemeFrame::viseme)
                          .containsExactly("TH", "E", "kk");
        assertThat(frames).extracting(VisemeFrame::startMs)
                          .containsExactly(0L, 100L, 200L);
    }

    @Test
    void collapsesConsecutiveIdenticalVisemes() {
        var phonemes = List.of(
                new PhonemeTiming("t", 0, 50),
                new PhonemeTiming("d", 50, 100),
                new PhonemeTiming("ɑ", 100, 200));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames).extracting(VisemeFrame::viseme)
                          .containsExactly("DD", "aa");
        assertThat(frames.get(0).startMs()).isEqualTo(0);
        assertThat(frames.get(0).endMs()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void emptyInputReturnsEmptyOutput() {
        assertThat(VisemeMapping.convert(List.of())).isEmpty();
    }

    @Test
    void singlePhonemeProducesSingleFrame() {
        var               phonemes = List.of(new PhonemeTiming("a", 0, 100));
        List<VisemeFrame> frames   = VisemeMapping.convert(phonemes);
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().viseme()).isEqualTo("aa");
        assertThat(frames.getFirst().weight()).isEqualTo(1.0);
    }

    // --- Weight tests ---

    @Test
    void openVowelsHaveHighestWeight() {
        assertThat(VisemeMapping.weightFor("aa")).isEqualTo(1.0);
    }

    @Test
    void consonantsHaveLowerWeightThanVowels() {
        assertThat(VisemeMapping.weightFor("DD")).isLessThan(VisemeMapping.weightFor("aa"));
        assertThat(VisemeMapping.weightFor("kk")).isLessThan(VisemeMapping.weightFor("aa"));
        assertThat(VisemeMapping.weightFor("nn")).isLessThan(VisemeMapping.weightFor("aa"));
    }

    @Test
    void silenceHasZeroWeight() {
        assertThat(VisemeMapping.weightFor("sil")).isEqualTo(0.0);
    }

    @Test
    void convertAssignsWeightsPerVisemeType() {
        var phonemes = List.of(
                new PhonemeTiming("ɑ", 0, 100),
                new PhonemeTiming("p", 100, 200),
                new PhonemeTiming("n", 200, 300));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames).extracting(VisemeFrame::weight)
                          .containsExactly(1.0, 0.8, 0.4);
    }

    @Test
    void unknownVisemeGetsDefaultWeight() {
        assertThat(VisemeMapping.weightFor("UNKNOWN")).isEqualTo(0.5);
    }

    // --- Minimum duration tests ---

    @Test
    void shortVisemeIsExtendedToMinimumDuration() {
        var               phonemes = List.of(new PhonemeTiming("a", 0, 30));
        List<VisemeFrame> frames   = VisemeMapping.convert(phonemes);
        assertThat(frames.getFirst().durationMs()).isGreaterThanOrEqualTo(VisemeMapping.MIN_DURATION_MS);
    }

    @Test
    void shortVisemeExtensionCapsAtNextFrameStart() {
        var phonemes = List.of(
                new PhonemeTiming("a", 0, 30),
                new PhonemeTiming("p", 50, 150));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames.get(0).endMs()).isLessThanOrEqualTo(frames.get(1).startMs());
    }

    @Test
    void visemesAlreadyAboveMinimumAreUnchanged() {
        var phonemes = List.of(
                new PhonemeTiming("a", 0, 150),
                new PhonemeTiming("p", 150, 300));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames.get(0).endMs()).isEqualTo(150);
        assertThat(frames.get(1).endMs()).isEqualTo(300);
    }
}
