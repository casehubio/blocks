package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StftUtilsTest {

    @Test void stftOutputShape() {
        float[] signal = generate440HzSine(16000, 0.5f);
        int nFft = 512;
        int hopLength = 128;
        float[][][] result = StftUtils.stft(signal, nFft, hopLength);
        assertThat(result.length).isEqualTo(2); // [real, imag]
        int nBins = nFft / 2 + 1;
        assertThat(result[0]).hasNumberOfRows(nBins);
        int expectedFrames = (signal.length - nFft) / hopLength + 1;
        assertThat(result[0][0]).hasSize(expectedFrames);
        assertThat(result[1]).hasNumberOfRows(nBins);
        assertThat(result[1][0]).hasSize(expectedFrames);
    }

    @Test void stftHiftParams() {
        // HiFT uses n_fft=16, hop_length=4
        float[] signal = new float[256];
        for (int i = 0; i < signal.length; i++) {
            signal[i] = (float) Math.sin(2 * Math.PI * i / 16);
        }
        float[][][] result = StftUtils.stft(signal, 16, 4);
        assertThat(result[0]).hasNumberOfRows(9); // 16/2 + 1
        int expectedFrames = (256 - 16) / 4 + 1; // 61
        assertThat(result[0][0]).hasSize(expectedFrames);
    }

    @Test void roundTripRecoversSignal() {
        float[] original = generate440HzSine(16000, 0.1f);
        int nFft = 512;
        int hopLength = 128;
        float[][][] stft = StftUtils.stft(original, nFft, hopLength);

        float[][] magnitude = new float[stft[0].length][];
        float[][] phase = new float[stft[0].length][];
        for (int i = 0; i < stft[0].length; i++) {
            magnitude[i] = new float[stft[0][i].length];
            phase[i] = new float[stft[0][i].length];
            for (int j = 0; j < stft[0][i].length; j++) {
                float re = stft[0][i][j];
                float im = stft[1][i][j];
                magnitude[i][j] = (float) Math.sqrt(re * re + im * im);
                phase[i][j] = (float) Math.atan2(im, re);
            }
        }

        float[] reconstructed = StftUtils.istft(magnitude, phase, nFft, hopLength);

        // Windowed region should match within tolerance
        int validStart = nFft / 2;
        int validEnd = Math.min(original.length, reconstructed.length) - nFft / 2;
        for (int i = validStart; i < validEnd; i++) {
            assertThat(reconstructed[i]).as("sample %d", i)
                    .isCloseTo(original[i], within(0.05f));
        }
    }

    @Test void hiftRoundTrip() {
        // Verify round-trip with HiFT-specific params
        float[] original = new float[256];
        for (int i = 0; i < original.length; i++) {
            original[i] = (float) (0.5 * Math.sin(2 * Math.PI * i / 16)
                    + 0.3 * Math.cos(2 * Math.PI * i / 8));
        }
        int nFft = 16;
        int hopLength = 4;
        float[][][] stft = StftUtils.stft(original, nFft, hopLength);

        float[][] magnitude = new float[stft[0].length][];
        float[][] phase = new float[stft[0].length][];
        for (int i = 0; i < stft[0].length; i++) {
            magnitude[i] = new float[stft[0][i].length];
            phase[i] = new float[stft[0][i].length];
            for (int j = 0; j < stft[0][i].length; j++) {
                float re = stft[0][i][j];
                float im = stft[1][i][j];
                magnitude[i][j] = (float) Math.sqrt(re * re + im * im);
                phase[i][j] = (float) Math.atan2(im, re);
            }
        }

        float[] reconstructed = StftUtils.istft(magnitude, phase, nFft, hopLength);

        int validStart = nFft;
        int validEnd = Math.min(original.length, reconstructed.length) - nFft;
        for (int i = validStart; i < validEnd; i++) {
            assertThat(reconstructed[i]).as("sample %d", i)
                    .isCloseTo(original[i], within(0.05f));
        }
    }

    @Test void dcSignalStftConcentratesInBinZero() {
        float[] dc = new float[512];
        java.util.Arrays.fill(dc, 1.0f);
        float[][][] result = StftUtils.stft(dc, 64, 16);
        for (int f = 0; f < result[0][0].length; f++) {
            float mag0 = (float) Math.sqrt(result[0][0][f] * result[0][0][f]
                    + result[1][0][f] * result[1][0][f]);
            assertThat(mag0).isGreaterThan(0f);
            // bin 0 should dominate (Hann window causes spectral leakage to adjacent bins)
            for (int b = 2; b < result[0].length; b++) {
                float mag = (float) Math.sqrt(result[0][b][f] * result[0][b][f]
                        + result[1][b][f] * result[1][b][f]);
                assertThat(mag).as("bin %d, frame %d", b, f).isLessThan(mag0);
            }
        }
    }

    @Test void shortSignalThrows() {
        float[] tooShort = new float[10];
        assertThatThrownBy(() -> StftUtils.stft(tooShort, 64, 16))
                .isInstanceOf(IllegalArgumentException.class);
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
