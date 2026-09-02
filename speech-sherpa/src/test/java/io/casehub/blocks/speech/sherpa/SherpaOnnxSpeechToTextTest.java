package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TranscriptionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SherpaOnnxSpeechToTextTest {

    @TempDir Path tempDir;

    @Test
    void rejectsNullAudioFile() {
        var stt = new SherpaOnnxSpeechToText(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> stt.transcribe(null, TranscriptionOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullOptions() {
        var stt = new SherpaOnnxSpeechToText(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> stt.transcribe(tempDir.resolve("test.wav"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void throwsOnInvalidAudioFile() throws IOException {
        Path notAWav = tempDir.resolve("notawav.bin");
        Files.write(notAWav, new byte[]{1, 2, 3, 4});

        var stt = new SherpaOnnxSpeechToText(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> stt.transcribe(notAWav, TranscriptionOptions.defaults()))
                .hasMessageContaining("Failed to read audio file")
                .hasCauseInstanceOf(java.io.IOException.class)
                .cause().hasMessageContaining("RIFF");
    }

    @Test
    void closeOnFreshInstanceDoesNotThrow() {
        var stt = new SherpaOnnxSpeechToText(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);
        stt.close();
    }

    @Test
    void closeIsIdempotent() {
        var stt = new SherpaOnnxSpeechToText(SherpaConfig.defaults(tempDir), (SherpaLibrary) null);
        stt.close();
        stt.close();
    }


    @Test
    @EnabledIf("hasModels")
    void transcribesWithSherpa() throws IOException {
        Path modelDir = Path.of(System.getProperty("sherpa.model.dir", "/tmp/sherpa-onnx/sherpa-onnx-whisper-tiny"));
        var  config   = SherpaConfig.defaults(modelDir);

        try (var stt = new SherpaOnnxSpeechToText(config)) {
            short[] silence = new short[16000];
            Path    wavFile = tempDir.resolve("silence.wav");
            Files.write(wavFile, WavReaderTest.buildWavBytes(1, 16000, 16, silence));

            var result1 = stt.transcribe(wavFile, TranscriptionOptions.defaults());
            assertThat(result1).isNotNull();
            assertThat(result1.text()).isNotNull();

            var result2 = stt.transcribe(wavFile, TranscriptionOptions.defaults());
            assertThat(result2).isNotNull();
            assertThat(result2.text()).isNotNull();
        }
    }

    static boolean hasModels() {
        return SherpaLibrary.isAvailable()
                && java.nio.file.Files.exists(Path.of(System.getProperty("sherpa.model.dir",
                        "/tmp/sherpa-onnx/sherpa-onnx-whisper-tiny/tiny-encoder.onnx")));
    }
}
