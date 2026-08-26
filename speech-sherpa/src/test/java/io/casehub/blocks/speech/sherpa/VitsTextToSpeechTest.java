package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("allDepsAvailable")
class VitsTextToSpeechTest {

    static final Path MODEL_DIR = Path.of("/tmp/piper-model-inspect/vits-piper-en_US-lessac-medium");
    static final Path ESPEAK_LIB = Path.of("/opt/homebrew/lib/libespeak-ng.dylib");
    static final Path ESPEAK_DATA = Path.of("/opt/homebrew/Cellar/espeak-ng/1.52.0/share/espeak-ng-data");

    @Test
    void synthesise_producesAudio() throws Exception {
        try (var tts = createTts()) {
            var result = tts.synthesise("hello", SynthesisOptions.defaults());
            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.audioFormat()).isEqualTo("wav");
        }
    }

    @Test
    void synthesise_withPhonemes_returnsTimings() throws Exception {
        try (var tts = createTts()) {
            var options = new SynthesisOptions(null, null, "wav", true);
            var result = tts.synthesise("hello world", options);
            assertThat(result.phonemes()).isNotEmpty();
            for (var pt : result.phonemes()) {
                assertThat(pt.startMs()).isGreaterThanOrEqualTo(0);
                assertThat(pt.endMs()).isGreaterThanOrEqualTo(pt.startMs());
                assertThat(pt.phoneme()).isNotEmpty();
            }
        }
    }

    @Test
    void synthesise_phonemeTimingsWithinAudioDuration() throws Exception {
        try (var tts = createTts()) {
            var options  = new SynthesisOptions(null, null, "wav", true);
            var result   = tts.synthesise("hello", options);
            var phonemes = result.phonemes();
            assertThat(phonemes).isNotEmpty();

            byte[] wav = result.audioData();
            int sr = (wav[24] & 0xFF) | ((wav[25] & 0xFF) << 8)
                     | ((wav[26] & 0xFF) << 16) | ((wav[27] & 0xFF) << 24);
            int  dataBytes = wav.length - 44;
            long audioMs   = (long) (dataBytes / 2.0 / sr * 1000);

            // All phoneme timings must fall within the audio duration
            for (var pt : phonemes) {
                assertThat(pt.startMs()).isGreaterThanOrEqualTo(0);
                assertThat(pt.endMs()).isLessThanOrEqualTo(audioMs + 1);
            }

            // Phonemes are monotonically increasing
            for (int i = 1; i < phonemes.size(); i++) {
                assertThat(phonemes.get(i).startMs())
                        .isGreaterThanOrEqualTo(phonemes.get(i - 1).startMs());
            }

            // Last phoneme ends before audio ends (pad durations fill the rest)
            long lastEnd = phonemes.getLast().endMs();
            assertThat(lastEnd).isLessThanOrEqualTo(audioMs);
            assertThat(lastEnd).isGreaterThan(0);
        }
    }

    @Test
    void synthesise_withoutPhonemes_returnsEmptyList() throws Exception {
        try (var tts = createTts()) {
            var result = tts.synthesise("hello", SynthesisOptions.defaults());
            assertThat(result.phonemes()).isEmpty();
        }
    }

    @Test
    void synthesise_multipleCallsReusesSession() throws Exception {
        try (var tts = createTts()) {
            var r1 = tts.synthesise("first", SynthesisOptions.defaults());
            var r2 = tts.synthesise("second", SynthesisOptions.defaults());
            assertThat(r1.audioData()).isNotEmpty();
            assertThat(r2.audioData()).isNotEmpty();
        }
    }

    static VitsTextToSpeech createTts() {
        var config = VitsConfig.fromModelDir(MODEL_DIR);
        var ort = OnnxRuntimeLibrary.load();
        var espeak = EspeakLibrary.load(ESPEAK_LIB, ESPEAK_DATA);
        return new VitsTextToSpeech(config, ort, espeak);
    }

    static boolean allDepsAvailable() {
        try {
            if (!Files.isDirectory(MODEL_DIR)) return false;
            if (!Files.exists(ESPEAK_LIB)) return false;
            if (!Files.isDirectory(ESPEAK_DATA)) return false;
            OnnxRuntimeLibrary.load();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
