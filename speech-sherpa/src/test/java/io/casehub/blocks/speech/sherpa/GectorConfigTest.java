package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GectorConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesTagVocabulary() throws Exception {
        var config = createTestConfig();
        assertThat(config.tagVocabulary()).hasSize(14);
        assertThat(config.tagVocabulary().getFirst()).isEqualTo("$KEEP");
        assertThat(config.tagVocabulary().get(1)).isEqualTo("$DELETE");
        assertThat(config.tagVocabulary().getLast()).isEqualTo("$MERGE_HYPHEN");
    }

    @Test
    void keepTagIdIsZero() throws Exception {
        var config = createTestConfig();
        assertThat(config.keepTagId()).isEqualTo(0);
    }

    @Test
    void defaultMaxIterations() throws Exception {
        var config = createTestConfig();
        assertThat(config.maxIterations()).isEqualTo(5);
    }

    @Test
    void defaultConfidenceThresholds() throws Exception {
        var config = createTestConfig();
        assertThat(config.keepConfidence()).isEqualTo(0.0f);
        assertThat(config.minErrorProb()).isEqualTo(0.0f);
    }

    @Test
    void defaultNumThreads() throws Exception {
        var config = createTestConfig();
        assertThat(config.numThreads()).isEqualTo(1);
    }

    @Test
    void modelPaths() throws Exception {
        var config = createTestConfig();
        assertThat(config.modelPath()).isEqualTo(tempDir.resolve("model.onnx"));
        assertThat(config.spModelPath()).isEqualTo(tempDir.resolve("spiece.model"));
    }

    @Test
    void parsesVerbDictionary() throws Exception {
        var config = createTestConfig();
        assertThat(config.verbDictionary()).containsKey("go");
        assertThat(config.verbDictionary().get("go")).containsEntry("VBZ", "goes");
        assertThat(config.verbDictionary().get("go")).containsEntry("VBG", "going");
        assertThat(config.verbDictionary().get("be")).containsEntry("VBZ", "is");
    }

    @Test
    void emptyVerbDictionaryWhenFileAbsent() throws Exception {
        writeLabels();
        Files.createFile(tempDir.resolve("model.onnx"));
        Files.createFile(tempDir.resolve("spiece.model"));
        var config = GectorConfig.fromModelDir(tempDir);
        assertThat(config.verbDictionary()).isEmpty();
    }

    @Test
    void tagVocabularyIsUnmodifiable() throws Exception {
        var config = createTestConfig();
        assertThatThrownBy(() -> config.tagVocabulary().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingLabelsThrows() {
        Files.exists(tempDir.resolve("labels.txt"));
        assertThatThrownBy(() -> GectorConfig.fromModelDir(tempDir))
                .isInstanceOf(java.io.IOException.class);
    }

    private GectorConfig createTestConfig() throws Exception {
        writeLabels();
        Files.createFile(tempDir.resolve("model.onnx"));
        Files.createFile(tempDir.resolve("spiece.model"));
        Files.writeString(tempDir.resolve("verb-form-vocab.txt"),
                "go\tVBZ\tgoes\ngo\tVBG\tgoing\nbe\tVBZ\tis\n");
        return GectorConfig.fromModelDir(tempDir);
    }

    private void writeLabels() throws Exception {
        try (var in = getClass().getResourceAsStream("/test-gector-labels.txt")) {
            Files.copy(in, tempDir.resolve("labels.txt"));
        }
    }
}
