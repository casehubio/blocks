package io.casehub.blocks.speech.sherpa;

final class AudioResampler {

    private static final int SINC_WINDOW_SIZE = 32;

    private AudioResampler() {}

    static float[] resample(float[] audio, int sourceSampleRate, int targetSampleRate) {
        if (audio.length == 0) return new float[0];
        if (sourceSampleRate == targetSampleRate) return audio.clone();

        double ratio = (double) targetSampleRate / sourceSampleRate;
        int outputLength = (int) Math.round(audio.length * ratio);
        if (outputLength == 0) return new float[0];

        float[] output = new float[outputLength];
        double cutoff = Math.min(1.0, ratio);
        int halfWindow = SINC_WINDOW_SIZE;

        for (int i = 0; i < outputLength; i++) {
            double srcPos = i / ratio;
            int srcIdx = (int) Math.floor(srcPos);
            double frac = srcPos - srcIdx;

            double sum = 0;
            double windowSum = 0;
            int start = Math.max(0, srcIdx - halfWindow + 1);
            int end = Math.min(audio.length - 1, srcIdx + halfWindow);

            for (int j = start; j <= end; j++) {
                double x = (j - srcPos) * cutoff;
                double sinc = (Math.abs(x) < 1e-10) ? 1.0 : Math.sin(Math.PI * x) / (Math.PI * x);
                double windowX = (j - srcPos) / halfWindow;
                double window = (Math.abs(windowX) <= 1.0)
                        ? 0.5 * (1.0 + Math.cos(Math.PI * windowX))
                        : 0.0;
                double weight = sinc * window * cutoff;
                sum += audio[j] * weight;
                windowSum += weight;
            }

            output[i] = (windowSum > 1e-10) ? (float) (sum / windowSum) : 0f;
        }
        return output;
    }
}
