package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TranscriptionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperSpeechToTextTest {

    static boolean isWhisperAvailable() {
        return WhisperLibrary.isAvailable();
    }

    @Test
    @EnabledIf("isWhisperAvailable")
    void startsStreamAndClosesCleanly() throws Exception {
        var stt = WhisperSpeechToText.withDefaults();
        try {
            var stream = stt.startStream(TranscriptionOptions.defaults());
            assertThat(stream).isNotNull();
            stream.close();
        } finally {
            stt.close();
        }
    }

    @Test
    @EnabledIf("isWhisperAvailable")
    void emptyBufferReturnEmptyResult() throws Exception {
        var stt = WhisperSpeechToText.withDefaults();
        try {
            var stream = stt.startStream(TranscriptionOptions.defaults());
            var result = stream.finalResult();
            assertThat(result.text()).isEmpty();
            stream.close();
        } finally {
            stt.close();
        }
    }

    @Test
    @EnabledIf("isWhisperAvailable")
    void acceptsSamplesWithoutError() throws Exception {
        var stt = WhisperSpeechToText.withDefaults();
        try {
            var stream = stt.startStream(TranscriptionOptions.defaults());
            float[] silence = new float[16000]; // 1 second of silence
            stream.acceptSamples(silence, 16000);
            assertThat(stream.partialResult()).isNotNull();
            stream.close();
        } finally {
            stt.close();
        }
    }

    @Test
    @EnabledIf("isWhisperAvailable")
    void vocabularyHintDoesNotCrash() throws Exception {
        var stt = WhisperSpeechToText.withDefaults();
        try {
            var opts = TranscriptionOptions.defaults().withVocabularyHint("limerick poetry");
            var stream = stt.startStream(opts);
            float[] silence = new float[16000];
            stream.acceptSamples(silence, 16000);
            var result = stream.finalResult();
            assertThat(result).isNotNull();
            stream.close();
        } finally {
            stt.close();
        }
    }

    @Test
    void rejectsSampleRateNot16kHz() {
        // This test doesn't need whisper — it tests the RecognitionStream validation
        // But we need a WhisperSpeechToText instance. Skip if whisper unavailable.
        if (!isWhisperAvailable()) return;
        var stt = WhisperSpeechToText.withDefaults();
        try {
            var stream = stt.startStream(TranscriptionOptions.defaults());
            assertThatThrownBy(() -> stream.acceptSamples(new float[100], 44100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("16kHz");
            stream.close();
        } finally {
            stt.close();
        }
    }
}
