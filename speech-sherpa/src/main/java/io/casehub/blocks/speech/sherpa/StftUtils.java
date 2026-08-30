package io.casehub.blocks.speech.sherpa;

final class StftUtils {

    private StftUtils() {}

    static float[][][] stft(float[] signal, int nFft, int hopLength) {
        if (signal.length < nFft) {
            throw new IllegalArgumentException(
                    "Signal length " + signal.length + " is less than nFft " + nFft);
        }

        int nBins = nFft / 2 + 1;
        int numFrames = (signal.length - nFft) / hopLength + 1;
        int fftSize = Fft.nextPowerOfTwo(nFft);
        float[] window = hannWindow(nFft);

        float[][] real = new float[nBins][numFrames];
        float[][] imag = new float[nBins][numFrames];

        for (int f = 0; f < numFrames; f++) {
            int offset = f * hopLength;
            float[] fftReal = new float[fftSize];
            float[] fftImag = new float[fftSize];
            for (int i = 0; i < nFft; i++) {
                fftReal[i] = signal[offset + i] * window[i];
            }
            Fft.forward(fftReal, fftImag);
            for (int b = 0; b < nBins; b++) {
                real[b][f] = fftReal[b];
                imag[b][f] = fftImag[b];
            }
        }
        return new float[][][] { real, imag };
    }

    static float[] istft(float[][] magnitude, float[][] phase,
                         int nFft, int hopLength) {
        int nBins = magnitude.length;
        int numFrames = magnitude[0].length;
        int fftSize = Fft.nextPowerOfTwo(nFft);
        float[] window = hannWindow(nFft);
        int outputLength = nFft + (numFrames - 1) * hopLength;
        float[] output = new float[outputLength];
        float[] windowSum = new float[outputLength];

        for (int f = 0; f < numFrames; f++) {
            float[] fftReal = new float[fftSize];
            float[] fftImag = new float[fftSize];

            for (int b = 0; b < nBins; b++) {
                fftReal[b] = magnitude[b][f] * (float) Math.cos(phase[b][f]);
                fftImag[b] = magnitude[b][f] * (float) Math.sin(phase[b][f]);
            }
            // mirror conjugate for full spectrum
            for (int b = 1; b < nBins - 1; b++) {
                fftReal[fftSize - b] = fftReal[b];
                fftImag[fftSize - b] = -fftImag[b];
            }

            Fft.inverse(fftReal, fftImag);

            int offset = f * hopLength;
            for (int i = 0; i < nFft; i++) {
                output[offset + i] += fftReal[i] * window[i];
                windowSum[offset + i] += window[i] * window[i];
            }
        }

        for (int i = 0; i < outputLength; i++) {
            if (windowSum[i] > 1e-8f) {
                output[i] /= windowSum[i];
            }
        }
        return output;
    }

    private static float[] hannWindow(int size) {
        float[] window = new float[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.5f * (1f - (float) Math.cos(2 * Math.PI * i / size));
        }
        return window;
    }
}
