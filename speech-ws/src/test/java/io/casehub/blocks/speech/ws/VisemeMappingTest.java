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
                new PhonemeTiming("ð", 0, 60),
                new PhonemeTiming("ə", 60, 120),
                new PhonemeTiming("k", 120, 200));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames).containsExactly(
                new VisemeFrame("TH", 0, 60),
                new VisemeFrame("E", 60, 120),
                new VisemeFrame("kk", 120, 200));
    }

    @Test
    void collapsesConsecutiveIdenticalVisemes() {
        var phonemes = List.of(
                new PhonemeTiming("t", 0, 50),
                new PhonemeTiming("d", 50, 100),
                new PhonemeTiming("ɑ", 100, 200));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames).containsExactly(
                new VisemeFrame("DD", 0, 100),
                new VisemeFrame("aa", 100, 200));
    }

    @Test
    void emptyInputReturnsEmptyOutput() {
        assertThat(VisemeMapping.convert(List.of())).isEmpty();
    }

    @Test
    void singlePhonemeProducesSingleFrame() {
        var phonemes = List.of(new PhonemeTiming("a", 0, 100));
        assertThat(VisemeMapping.convert(phonemes)).containsExactly(
                new VisemeFrame("aa", 0, 100));
    }
}
