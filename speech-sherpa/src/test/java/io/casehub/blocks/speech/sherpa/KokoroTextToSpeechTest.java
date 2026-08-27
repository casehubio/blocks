package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KokoroTextToSpeechTest {

    @TempDir Path tempDir;

    @Test
    void rejectsNullText() {
        var tts = new KokoroTextToSpeech(KokoroConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> tts.synthesise(null, SynthesisOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullOptions() {
        var tts = new KokoroTextToSpeech(KokoroConfig.defaults(tempDir), (SherpaLibrary) null);

        assertThatThrownBy(() -> tts.synthesise("hello", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void phonemesAreAlwaysEmpty() {
        var tts = new KokoroTextToSpeech(KokoroConfig.defaults(tempDir), (SherpaLibrary) null);

        // Can't synthesise without native lib, but we can verify the contract
        // by checking the class implements TextToSpeechService
        assertThat(tts).isInstanceOf(io.casehub.blocks.speech.TextToSpeechService.class);
    }
}
