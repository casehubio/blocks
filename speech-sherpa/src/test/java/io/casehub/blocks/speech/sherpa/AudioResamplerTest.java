package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AudioResamplerTest {

    @Test void downsample48kTo16k() {
        float[] audio = generate440HzSine(48000, 0.5f);
        float[] resampled = AudioResampler.resample(audio, 48000, 16000);
        int expectedLength = audio.length * 16000 / 48000;
        assertThat(resampled.length).isCloseTo(expectedLength, within(2));
    }

    @Test void downsample48kTo24k() {
        float[] audio = generate440HzSine(48000, 0.5f);
        float[] resampled = AudioResampler.resample(audio, 48000, 24000);
        int expectedLength = audio.length * 24000 / 48000;
        assertThat(resampled.length).isCloseTo(expectedLength, within(2));
    }

    @Test void downsample44_1kTo16k() {
        float[] audio = generate440HzSine(44100, 0.5f);
        float[] resampled = AudioResampler.resample(audio, 44100, 16000);
        int expectedLength = (int) ((long) audio.length * 16000 / 44100);
        assertThat(resampled.length).isCloseTo(expectedLength, within(2));
    }

    @Test void sameSampleRateReturnsIdentity() {
        float[] audio = generate440HzSine(16000, 0.1f);
        float[] resampled = AudioResampler.resample(audio, 16000, 16000);
        assertThat(resampled).hasSize(audio.length);
        for (int i = 0; i < audio.length; i++) {
            assertThat(resampled[i]).isCloseTo(audio[i], within(1e-6f));
        }
    }

    @Test void preservesSineFrequencyAfterDownsample() {
        int sourceRate = 48000;
        int targetRate = 16000;
        float freq = 440f;
        float duration = 0.5f;
        float[] audio = generate440HzSine(sourceRate, duration);
        float[] resampled = AudioResampler.resample(audio, sourceRate, targetRate);

        // Verify peak frequency is still ~440Hz by checking zero crossings
        int zeroCrossings = 0;
        for (int i = 1; i < resampled.length; i++) {
            if ((resampled[i - 1] >= 0 && resampled[i] < 0)
                    || (resampled[i - 1] < 0 && resampled[i] >= 0)) {
                zeroCrossings++;
            }
        }
        float detectedFreq = zeroCrossings * targetRate / (2f * resampled.length);
        assertThat(detectedFreq).isCloseTo(freq, within(10f));
    }

    @Test void outputAmplitudePreserved() {
        float[] audio = generate440HzSine(48000, 0.5f);
        float[] resampled = AudioResampler.resample(audio, 48000, 16000);
        float maxAbs = 0;
        for (float v : resampled) {
            if (Math.abs(v) > maxAbs) maxAbs = Math.abs(v);
        }
        // Amplitude should be close to 1.0 (the original sine amplitude)
        assertThat(maxAbs).isBetween(0.85f, 1.15f);
    }

    @Test void upsample16kTo24k() {
        float[] audio = generate440HzSine(16000, 0.5f);
        float[] resampled = AudioResampler.resample(audio, 16000, 24000);
        int expectedLength = audio.length * 24000 / 16000;
        assertThat(resampled.length).isCloseTo(expectedLength, within(2));
    }

    @Test void silenceRemainsZero() {
        float[] silence = new float[4800];
        float[] resampled = AudioResampler.resample(silence, 48000, 16000);
        for (float v : resampled) {
            assertThat(v).isCloseTo(0f, within(1e-6f));
        }
    }

    @Test void emptyInputReturnsEmpty() {
        float[] empty = new float[0];
        float[] resampled = AudioResampler.resample(empty, 48000, 16000);
        assertThat(resampled).isEmpty();
    }

    private static float[] generate440HzSine(int sampleRate, float durationSeconds) {
        int n = (int) (sampleRate * durationSeconds);
        float[] audio = new float[n];
        for (int i = 0; i < n; i++) {
            audio[i] = (float) Math.sin(2 * Math.PI * 440 * i / sampleRate);
        }
        return audio;
    }
}
