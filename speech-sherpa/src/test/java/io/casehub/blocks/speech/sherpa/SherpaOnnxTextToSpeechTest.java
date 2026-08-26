package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SherpaOnnxTextToSpeechTest {

    @TempDir Path tempDir;

    @Test
    void rejectsNullText() {
        var tts = new SherpaOnnxTextToSpeech(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> tts.synthesise(null, SynthesisOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullOptions() {
        var tts = new SherpaOnnxTextToSpeech(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> tts.synthesise("hello", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findTtsModel_singleOnnxFile() throws Exception {
        java.nio.file.Files.createFile(tempDir.resolve("en_US-amy-low.onnx"));
        assertThat(SherpaOnnxTextToSpeech.findTtsModel(tempDir)).isEqualTo(tempDir.resolve("en_US-amy-low.onnx").toString());
    }

    @Test
    void findTtsModel_prefersModelOnnx() throws Exception {
        java.nio.file.Files.createFile(tempDir.resolve("en_US-amy-low.onnx"));
        java.nio.file.Files.createFile(tempDir.resolve("model.onnx"));
        assertThat(SherpaOnnxTextToSpeech.findTtsModel(tempDir)).isEqualTo(tempDir.resolve("model.onnx").toString());
    }

    @Test
    void findTtsModel_multipleWithoutModelOnnx() throws Exception {
        java.nio.file.Files.createFile(tempDir.resolve("b-model.onnx"));
        java.nio.file.Files.createFile(tempDir.resolve("a-model.onnx"));
        assertThat(SherpaOnnxTextToSpeech.findTtsModel(tempDir)).isEqualTo(tempDir.resolve("a-model.onnx").toString());
    }

    @Test
    void findTtsModel_noOnnxFiles() {
        assertThatThrownBy(() -> SherpaOnnxTextToSpeech.findTtsModel(tempDir))
                .isInstanceOf(SherpaException.class)
                .hasMessageContaining("No TTS model");
    }


    @Test
    @EnabledIf("hasTtsModels")
    void synthesisesWithSherpa() {
        Path modelDir = Path.of(System.getProperty("sherpa.tts.model.dir", "/tmp/sherpa-onnx/vits-model"));
        var config = SherpaConfig.defaults(modelDir);
        var tts = new SherpaOnnxTextToSpeech(config);

        var result = tts.synthesise("Hello world", SynthesisOptions.defaults());
        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
    }

    static boolean hasTtsModels() {
        if (!SherpaLibrary.isAvailable()) {return false;}
        java.nio.file.Path modelDir = java.nio.file.Path.of(System.getProperty("sherpa.tts.model.dir",
                                                                               "/tmp/sherpa-onnx/vits-model"));
        try (var files = java.nio.file.Files.list(modelDir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".onnx"));
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
