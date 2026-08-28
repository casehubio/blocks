package io.casehub.blocks.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SpeechToTextServiceTest {

    @TempDir Path tempDir;

    @Test
    void transcriptionResultRejectsNullText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TranscriptionResult(null, "en", 0.9));
    }

    @Test
    void transcriptionResultStoresAllFields() {
        final var result = new TranscriptionResult("hello world", "en", 0.95);
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void transcriptionOptionsDefaultsReturnsTinyWav() {
        final var opts = TranscriptionOptions.defaults();
        assertThat(opts.audioFormat()).isEqualTo("wav");
        assertThat(opts.modelSize()).isEqualTo("base.en");
        assertThat(opts.languageHint()).isNull();
    }

    @Test
    void stubImplementationTranscribesFile() throws IOException {
        final Path audioFile = tempDir.resolve("test.wav");
        Files.writeString(audioFile, "fake audio");

        final SpeechToTextService stt = (file, options) ->
                new TranscriptionResult("transcribed: " + file.getFileName(), "en", 1.0);

        final var result = stt.transcribe(audioFile, TranscriptionOptions.defaults());
        assertThat(result.text()).isEqualTo("transcribed: test.wav");
        assertThat(result.confidence()).isEqualTo(1.0);
    }
}
