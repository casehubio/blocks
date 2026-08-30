package io.casehub.blocks.speech.sherpa;

import java.util.Arrays;

final class MelSpectrogram {

    private MelSpectrogram() {}

    static float[][] compute(float[] audio, MelConfig config) {
        int nFft = config.nFft();
        int hopLength = config.hopLength();
        if (audio.length < nFft) {
            throw new IllegalArgumentException(
                    "Audio length " + audio.length + " is less than nFft " + nFft);
        }

        int nBins = nFft / 2 + 1;
        int numFrames = (audio.length - nFft) / hopLength + 1;
        float[] window = hannWindow(nFft);
        float[][] filterBank = melFilterBank(config.nMels(), nFft, config.sampleRate(),
                config.fMin(), config.fMax());

        int fftSize = Fft.nextPowerOfTwo(nFft);
        float[][] mel = new float[config.nMels()][numFrames];

        for (int f = 0; f < numFrames; f++) {
            int offset = f * hopLength;
            float[] real = new float[fftSize];
            float[] imag = new float[fftSize];
            for (int i = 0; i < nFft; i++) {
                real[i] = audio[offset + i] * window[i];
            }
            Fft.forward(real, imag);

            float[] powerSpectrum = new float[nBins];
            for (int i = 0; i < nBins; i++) {
                powerSpectrum[i] = real[i] * real[i] + imag[i] * imag[i];
            }

            for (int m = 0; m < config.nMels(); m++) {
                float sum = 0;
                for (int i = 0; i < nBins; i++) {
                    sum += filterBank[m][i] * powerSpectrum[i];
                }
                mel[m][f] = sum;
            }
        }
        return mel;
    }

    static float[][] logMel(float[][] mel) {
        float[][] result = new float[mel.length][];
        for (int m = 0; m < mel.length; m++) {
            result[m] = new float[mel[m].length];
            for (int f = 0; f < mel[m].length; f++) {
                result[m][f] = (float) Math.log(Math.max(mel[m][f], 1e-10));
            }
        }
        return result;
    }

    static float[][] whisperNormalize(float[][] logMel) {
        float maxVal = Float.NEGATIVE_INFINITY;
        for (float[] row : logMel) {
            for (float v : row) {
                if (v > maxVal) maxVal = v;
            }
        }
        float floor = maxVal - 8.0f;
        float[][] result = new float[logMel.length][];
        for (int m = 0; m < logMel.length; m++) {
            result[m] = new float[logMel[m].length];
            for (int f = 0; f < logMel[m].length; f++) {
                result[m][f] = (Math.max(logMel[m][f], floor) + 4.0f) / 4.0f;
            }
        }
        return result;
    }

    static void meanNormalize(float[][] mel) {
        for (float[] row : mel) {
            float sum = 0;
            for (float v : row) sum += v;
            float mean = sum / row.length;
            for (int i = 0; i < row.length; i++) {
                row[i] -= mean;
            }
        }
    }

    static float[][] melFilterBank(int nMels, int nFft, int sampleRate,
                                    float fMin, float fMax) {
        int nBins = nFft / 2 + 1;
        float[] melPoints = new float[nMels + 2];
        float melMin = hzToMel(fMin);
        float melMax = hzToMel(fMax);
        for (int i = 0; i < nMels + 2; i++) {
            melPoints[i] = melMin + (melMax - melMin) * i / (nMels + 1);
        }

        float[] hzPoints = new float[nMels + 2];
        for (int i = 0; i < nMels + 2; i++) {
            hzPoints[i] = melToHz(melPoints[i]);
        }

        float[] fftFreqs = new float[nBins];
        for (int i = 0; i < nBins; i++) {
            fftFreqs[i] = (float) sampleRate * i / nFft;
        }

        float[][] bank = new float[nMels][nBins];
        for (int m = 0; m < nMels; m++) {
            float left = hzPoints[m];
            float center = hzPoints[m + 1];
            float right = hzPoints[m + 2];
            for (int i = 0; i < nBins; i++) {
                float freq = fftFreqs[i];
                if (freq >= left && freq <= center && center > left) {
                    bank[m][i] = (freq - left) / (center - left);
                } else if (freq > center && freq <= right && right > center) {
                    bank[m][i] = (right - freq) / (right - center);
                }
            }
        }
        return bank;
    }

    private static float hzToMel(float hz) {
        return 2595f * (float) Math.log10(1f + hz / 700f);
    }

    private static float melToHz(float mel) {
        return 700f * ((float) Math.pow(10f, mel / 2595f) - 1f);
    }

    private static float[] hannWindow(int size) {
        float[] window = new float[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.5f * (1f - (float) Math.cos(2 * Math.PI * i / size));
        }
        return window;
    }
}
