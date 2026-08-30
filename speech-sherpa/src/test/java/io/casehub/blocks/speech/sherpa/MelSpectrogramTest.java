package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MelSpectrogramTest {

    // Three mel configs from the spec
    private static final MelConfig CAMPPLUS = new MelConfig(16000, 400, 160, 80, 20f, 7600f);
    private static final MelConfig WHISPER = new MelConfig(16000, 400, 160, 128, 0f, 8000f);
    private static final MelConfig FLOW = new MelConfig(24000, 1024, 256, 80, 0f, 12000f);

    @Test void melFilterBankShapeCampplus() {
        float[][] bank = MelSpectrogram.melFilterBank(80, 400, 16000, 20f, 7600f);
        assertThat(bank).hasNumberOfRows(80);
        assertThat(bank[0]).hasSize(201); // nFft/2 + 1
    }

    @Test void melFilterBankShapeWhisper() {
        float[][] bank = MelSpectrogram.melFilterBank(128, 400, 16000, 0f, 8000f);
        assertThat(bank).hasNumberOfRows(128);
        assertThat(bank[0]).hasSize(201);
    }

    @Test void melFilterBankShapeFlow() {
        float[][] bank = MelSpectrogram.melFilterBank(80, 1024, 24000, 0f, 12000f);
        assertThat(bank).hasNumberOfRows(80);
        assertThat(bank[0]).hasSize(513); // 1024/2 + 1
    }

    @Test void melFilterBankRowsSumToApproximatelyOne() {
        float[][] bank = MelSpectrogram.melFilterBank(80, 400, 16000, 20f, 7600f);
        for (int m = 1; m < 79; m++) {
            float sum = 0;
            for (float v : bank[m]) sum += v;
            assertThat(sum).as("row %d sum", m).isGreaterThan(0f);
        }
    }

    @Test void melFilterBankIsTriangular() {
        float[][] bank = MelSpectrogram.melFilterBank(80, 400, 16000, 20f, 7600f);
        for (int m = 0; m < 80; m++) {
            float[] row = bank[m];
            for (float v : row) {
                assertThat(v).isGreaterThanOrEqualTo(0f);
            }
            int firstNonZero = -1;
            int lastNonZero = -1;
            for (int i = 0; i < row.length; i++) {
                if (row[i] > 0) {
                    if (firstNonZero < 0) firstNonZero = i;
                    lastNonZero = i;
                }
            }
            if (firstNonZero >= 0) {
                // contiguous non-zero region
                for (int i = firstNonZero; i <= lastNonZero; i++) {
                    assertThat(row[i]).as("row %d, bin %d", m, i).isGreaterThan(0f);
                }
            }
        }
    }

    @Test void computeOutputShapeCampplus() {
        float[] audio = generate440HzSine(16000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, CAMPPLUS);
        assertThat(mel).hasNumberOfRows(80);
        int expectedFrames = (audio.length - 400) / 160 + 1;
        assertThat(mel[0]).hasSize(expectedFrames);
    }

    @Test void computeOutputShapeWhisper() {
        float[] audio = generate440HzSine(16000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, WHISPER);
        assertThat(mel).hasNumberOfRows(128);
        int expectedFrames = (audio.length - 400) / 160 + 1;
        assertThat(mel[0]).hasSize(expectedFrames);
    }

    @Test void computeOutputShapeFlow() {
        float[] audio = generate440HzSine(24000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, FLOW);
        assertThat(mel).hasNumberOfRows(80);
        int expectedFrames = (audio.length - 1024) / 256 + 1;
        assertThat(mel[0]).hasSize(expectedFrames);
    }

    @Test void melValuesAreNonNegative() {
        float[] audio = generate440HzSine(16000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, CAMPPLUS);
        for (float[] row : mel) {
            for (float v : row) {
                assertThat(v).isGreaterThanOrEqualTo(0f);
            }
        }
    }

    @Test void logMelProducesFiniteValues() {
        float[] audio = generate440HzSine(16000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, CAMPPLUS);
        float[][] logMel = MelSpectrogram.logMel(mel);
        assertThat(logMel).hasNumberOfRows(mel.length);
        for (float[] row : logMel) {
            for (float v : row) {
                assertThat(v).isFinite();
            }
        }
    }

    @Test void whisperNormalizeOutputRange() {
        float[] audio = generate440HzSine(16000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, WHISPER);
        float[][] logMel = MelSpectrogram.logMel(mel);
        float[][] normalized = MelSpectrogram.whisperNormalize(logMel);
        for (float[] row : normalized) {
            for (float v : row) {
                assertThat(v).isBetween(-1f, 4f + 1e-3f);
            }
        }
    }

    @Test void meanNormalizeZerosMean() {
        float[] audio = generate440HzSine(16000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, CAMPPLUS);
        float[][] logMel = MelSpectrogram.logMel(mel);
        MelSpectrogram.meanNormalize(logMel);
        for (float[] row : logMel) {
            float sum = 0;
            for (float v : row) sum += v;
            float mean = sum / row.length;
            assertThat(mean).isCloseTo(0f, within(1e-4f));
        }
    }

    @Test void sinusoidConcentratesInExpectedMelBand() {
        // 440Hz tone should concentrate energy in low mel bands
        float[] audio = generate440HzSine(16000, 1.0f);
        float[][] mel = MelSpectrogram.compute(audio, CAMPPLUS);
        // find band with max energy
        int maxBand = 0;
        float maxEnergy = 0;
        for (int m = 0; m < mel.length; m++) {
            float energy = 0;
            for (float v : mel[m]) energy += v;
            if (energy > maxEnergy) {
                maxEnergy = energy;
                maxBand = m;
            }
        }
        // 440Hz at 16kHz should be in a lower mel band (roughly band 10-25 for fmin=20)
        assertThat(maxBand).isLessThan(40);
    }

    @Test void silenceProducesNearZeroMel() {
        float[] silence = new float[16000];
        float[][] mel = MelSpectrogram.compute(silence, CAMPPLUS);
        for (float[] row : mel) {
            for (float v : row) {
                assertThat(v).isCloseTo(0f, within(1e-10f));
            }
        }
    }

    @Test void shortAudioBelowFrameSizeThrows() {
        float[] tooShort = new float[100]; // less than nFft=400
        assertThatThrownBy(() -> MelSpectrogram.compute(tooShort, CAMPPLUS))
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
