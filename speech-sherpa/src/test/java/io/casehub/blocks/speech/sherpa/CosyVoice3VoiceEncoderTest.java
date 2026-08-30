package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CosyVoice3VoiceEncoderTest {

    private static final CosyVoice3Manifest MANIFEST = testManifest();

    @Test void producesEmbeddingVoiceData() {
        var encoder = testEncoder();
        byte[] wavData = makeTestWav(16000, 0.5f);
        VoiceData result = encoder.encode(wavData, "hello world");
        assertThat(result).isInstanceOf(VoiceData.EmbeddingVoiceData.class);
    }

    @Test void embeddingHasAllFields() {
        var encoder = testEncoder();
        byte[] wavData = makeTestWav(16000, 0.5f);
        var result = (VoiceData.EmbeddingVoiceData) encoder.encode(wavData, "hello world");
        assertThat(result.speakerEmbedding()).isNotEmpty();
        assertThat(result.speechTokens()).isNotEmpty();
        assertThat(result.promptMel()).isNotEmpty();
        assertThat(result.promptText()).isEqualTo("hello world");
    }

    @Test void speakerEmbeddingSize192() {
        var encoder = testEncoder();
        byte[] wavData = makeTestWav(16000, 0.5f);
        var result = (VoiceData.EmbeddingVoiceData) encoder.encode(wavData, "test");
        assertThat(result.speakerEmbedding()).hasSize(192);
    }

    @Test void nullTranscriptThrows() {
        var encoder = testEncoder();
        byte[] wavData = makeTestWav(16000, 0.5f);
        assertThatThrownBy(() -> encoder.encode(wavData, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void blankTranscriptThrows() {
        var encoder = testEncoder();
        byte[] wavData = makeTestWav(16000, 0.5f);
        assertThatThrownBy(() -> encoder.encode(wavData, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void implementsTtsVoiceEncoder() {
        var encoder = testEncoder();
        assertThat(encoder).isInstanceOf(TtsVoiceEncoder.class);
    }

    @Test void promptMelIsFromFlowConfig() {
        // Flow mel config: 24kHz, nFft=1024, hop=256, 80 mels
        // For 0.5s at 24kHz = 12000 samples, frames = (12000-1024)/256+1 = 43
        float[] captured24k = new float[1];
        var encoder = new CosyVoice3VoiceEncoder(
                mel16k -> {
                    // campplus: receives 80-band mel at 16kHz
                    return new float[192];
                },
                mel16k -> {
                    // speech tokenizer: receives 128-band mel at 16kHz
                    return new int[]{10, 20, 30};
                },
                MANIFEST
        );
        byte[] wavData = makeTestWav(24000, 0.5f);
        var result = (VoiceData.EmbeddingVoiceData) encoder.encode(wavData, "test");
        // promptMel should be the flattened 80-band mel at 24kHz
        assertThat(result.promptMel()).isNotEmpty();
    }

    @Test void resamplesInputAudio() {
        // Input at 48kHz should be resampled to 16kHz for campplus/speech_tokenizer
        // and 24kHz for prompt mel
        boolean[] called = {false, false};
        var encoder = new CosyVoice3VoiceEncoder(
                mel16k -> {
                    called[0] = true;
                    assertThat(mel16k).hasNumberOfRows(80);
                    return new float[192];
                },
                mel16k -> {
                    called[1] = true;
                    assertThat(mel16k).hasNumberOfRows(128);
                    return new int[]{1, 2};
                },
                MANIFEST
        );
        byte[] wavData = makeTestWav(48000, 0.5f);
        encoder.encode(wavData, "test");
        assertThat(called[0]).as("campplus called").isTrue();
        assertThat(called[1]).as("speech tokenizer called").isTrue();
    }

    @Test void camppusMelIsMeanNormalized() {
        var encoder = new CosyVoice3VoiceEncoder(
                mel16k -> {
                    // campplus mel should be mean-normalized (per the spec)
                    // Verify rows have near-zero mean
                    for (float[] row : mel16k) {
                        float sum = 0;
                        for (float v : row) sum += v;
                        float mean = sum / row.length;
                        assertThat(mean).as("campplus mel row mean").isCloseTo(0f, within(0.01f));
                    }
                    return new float[192];
                },
                mel16k -> new int[]{1},
                MANIFEST
        );
        byte[] wavData = makeTestWav(16000, 1.0f);
        encoder.encode(wavData, "test");
    }

    // --- Helpers ---

    private static CosyVoice3VoiceEncoder testEncoder() {
        return new CosyVoice3VoiceEncoder(
                mel16k -> new float[192],
                mel16k -> new int[]{10, 20, 30, 40},
                MANIFEST
        );
    }

    private static byte[] makeTestWav(int sampleRate, float durationSeconds) {
        int n = (int) (sampleRate * durationSeconds);
        float[] samples = new float[n];
        for (int i = 0; i < n; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * 440 * i / sampleRate);
        }
        return WavWriter.encode(samples, sampleRate, 1);
    }

    private static CosyVoice3Manifest testManifest() {
        var header = new PipelineHeader("cosyvoice3-test", 24000,
                Map.of(), null, Map.of());
        return new CosyVoice3Manifest(header, 896, 6561, 6561, 6562, 6563,
                24, 64, 2, 10, 80, 16, 4, 192, "tokenizer", Map.of());
    }
}
