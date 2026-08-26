package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VitsConfigTest {

    @TempDir
    static Path tempDir;

    static VitsConfig config;

    @BeforeAll
    static void setUp() throws IOException {
        Path configJson = tempDir.resolve("test-model.onnx.json");
        try (var in = VitsConfigTest.class.getResourceAsStream("/test-vits-config.json")) {
            Files.copy(in, configJson);
        }
        // Create a dummy .onnx file so fromModelDir can find it
        Files.writeString(tempDir.resolve("test-model.onnx"), "dummy");
        config = VitsConfig.fromModelDir(tempDir);
    }

    @Test
    void fromModelDir_parsesSampleRate() {
        assertThat(config.sampleRate()).isEqualTo(22050);
    }

    @Test
    void fromModelDir_parsesEspeakVoice() {
        assertThat(config.espeakVoice()).isEqualTo("en-us");
    }

    @Test
    void fromModelDir_parsesInferenceDefaults() {
        assertThat(config.noiseScale()).isCloseTo(0.667f, within(0.001f));
        assertThat(config.lengthScale()).isCloseTo(1.0f, within(0.001f));
        assertThat(config.noiseScaleW()).isCloseTo(0.8f, within(0.001f));
    }

    @Test
    void fromModelDir_defaultsRuntimeConfig() {
        assertThat(config.numThreads()).isEqualTo(1);
        assertThat(config.provider()).isEqualTo("cpu");
    }

    @Test
    void phonemeToIds_singleId() {
        assertThat(config.phonemeToIds("h")).containsExactly(20);
    }

    @Test
    void phonemeToIds_multiId() {
        assertThat(config.phonemeToIds("oʊ")).containsExactly(40, 41);
    }

    @Test
    void phonemeToIds_unknownReturnsEmpty() {
        assertThat(config.phonemeToIds("ʒ")).isEmpty();
    }

    @Test
    void idToPhoneme_singleIdEntry() {
        assertThat(config.idToPhoneme(20)).isEqualTo("h");
    }

    @Test
    void idToPhoneme_multiIdEntry_firstIdMaps() {
        assertThat(config.idToPhoneme(40)).isEqualTo("oʊ");
    }

    @Test
    void idToPhoneme_unknownReturnsNull() {
        assertThat(config.idToPhoneme(999)).isNull();
    }

    @Test
    void tokenize_interspersesPadTokens() {
        // "hə" → raw [BOS, h, ə, EOS] = 4 tokens
        // interspersed: [PAD, BOS, PAD, h, PAD, ə, PAD, EOS, PAD] = 2*4+1 = 9
        List<Integer> ids = config.tokenize("hə");
        assertThat(ids).startsWith(0, 1, 0); // PAD, BOS, PAD
        assertThat(ids).endsWith(0, 2, 0);   // PAD, EOS, PAD
        assertThat(ids).hasSize(9);
    }

    @Test
    void tokenize_greedyLongestMatch() {
        // "oʊ" matches as multi-char [40,41], not separate "o" + "ʊ"
        List<Integer> ids = config.tokenize("oʊ");
        assertThat(ids).contains(40, 41);
    }

    @Test
    void tokenize_unknownCharsSkipped() {
        // "hXə" — X not in map, skipped
        List<Integer> ids = config.tokenize("hXə");
        assertThat(ids).contains(20, 59);
    }

    @Test
    void tokenize_spaceInsertsWordBoundary() {
        List<Integer> ids = config.tokenize("h d");
        assertThat(ids).contains(3); // space token
    }

    @Test
    void tokenize_multiIdExpanded() {
        // "tʃ" maps to [90, 91] — both should appear in interspersed output
        List<Integer> ids = config.tokenize("tʃ");
        // raw: [BOS, 90, 91, EOS] = 4 tokens
        // interspersed: [PAD, BOS, PAD, 90, PAD, 91, PAD, EOS, PAD] = 9
        assertThat(ids).hasSize(9);
        assertThat(ids).contains(90, 91);
    }
}
