package io.casehub.blocks.speech.sherpa;

final class Fft {

    private Fft() {}

    static void forward(float[] real, float[] imag) {
        validate(real, imag);
        transform(real, imag, false);
    }

    static void inverse(float[] real, float[] imag) {
        validate(real, imag);
        transform(real, imag, true);
        int n = real.length;
        for (int i = 0; i < n; i++) {
            real[i] /= n;
            imag[i] /= n;
        }
    }

    static int nextPowerOfTwo(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive: " + n);
        return Integer.highestOneBit(n - 1) << 1 > 0
                ? Integer.highestOneBit(n - 1) << 1
                : 1;
    }

    private static void validate(float[] real, float[] imag) {
        if (real.length != imag.length) {
            throw new IllegalArgumentException(
                    "real and imag arrays must have the same length: " + real.length + " vs " + imag.length);
        }
        int n = real.length;
        if (n == 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("Length must be a power of two: " + n);
        }
    }

    private static void transform(float[] real, float[] imag, boolean inverse) {
        int n = real.length;
        int bits = Integer.numberOfTrailingZeros(n);

        // bit-reversal permutation
        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - bits);
            if (i < j) {
                float tmpR = real[i]; real[i] = real[j]; real[j] = tmpR;
                float tmpI = imag[i]; imag[i] = imag[j]; imag[j] = tmpI;
            }
        }

        // Cooley-Tukey butterfly
        for (int size = 2; size <= n; size <<= 1) {
            int halfSize = size >> 1;
            double angleStep = (inverse ? 2.0 : -2.0) * Math.PI / size;
            double wReal = Math.cos(angleStep);
            double wImag = Math.sin(angleStep);

            for (int start = 0; start < n; start += size) {
                double curReal = 1.0;
                double curImag = 0.0;
                for (int k = 0; k < halfSize; k++) {
                    int even = start + k;
                    int odd = start + k + halfSize;
                    double tR = curReal * real[odd] - curImag * imag[odd];
                    double tI = curReal * imag[odd] + curImag * real[odd];
                    real[odd] = (float) (real[even] - tR);
                    imag[odd] = (float) (imag[even] - tI);
                    real[even] = (float) (real[even] + tR);
                    imag[even] = (float) (imag[even] + tI);
                    double newReal = curReal * wReal - curImag * wImag;
                    curImag = curReal * wImag + curImag * wReal;
                    curReal = newReal;
                }
            }
        }
    }
}
