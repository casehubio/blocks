package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FftTest {

    @Test void dcSignalConcentratesInBinZero() {
        float[] real = {1, 1, 1, 1, 1, 1, 1, 1};
        float[] imag = new float[8];
        Fft.forward(real, imag);
        assertThat(real[0]).isCloseTo(8f, within(1e-6f));
        for (int i = 1; i < 8; i++) {
            assertThat(real[i]).isCloseTo(0f, within(1e-6f));
            assertThat(imag[i]).isCloseTo(0f, within(1e-6f));
        }
    }

    @Test void impulseHasFlatSpectrum() {
        float[] real = {1, 0, 0, 0, 0, 0, 0, 0};
        float[] imag = new float[8];
        Fft.forward(real, imag);
        for (int i = 0; i < 8; i++) {
            assertThat(real[i]).isCloseTo(1f, within(1e-6f));
            assertThat(imag[i]).isCloseTo(0f, within(1e-6f));
        }
    }

    @Test void pureSinusoidConcentratesInSingleBin() {
        int n = 8;
        float[] real = new float[n];
        float[] imag = new float[n];
        int k = 1;
        for (int i = 0; i < n; i++) {
            real[i] = (float) Math.cos(2 * Math.PI * k * i / n);
        }
        Fft.forward(real, imag);
        assertThat(real[k]).isCloseTo(n / 2f, within(1e-5f));
        assertThat(real[n - k]).isCloseTo(n / 2f, within(1e-5f));
        for (int i = 0; i < n; i++) {
            if (i != k && i != n - k) {
                float magnitude = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
                assertThat(magnitude).isCloseTo(0f, within(1e-5f));
            }
        }
    }

    @Test void inverseRoundTrips() {
        float[] original = {0.3f, -1.2f, 0.7f, 2.1f, -0.5f, 0.9f, -0.1f, 1.4f};
        float[] real = original.clone();
        float[] imag = new float[8];
        Fft.forward(real, imag);
        Fft.inverse(real, imag);
        for (int i = 0; i < 8; i++) {
            assertThat(real[i]).isCloseTo(original[i], within(1e-5f));
        }
    }

    @Test void size16MatchesNumpyReference() {
        // numpy.fft.fft([1, 0, -1, 0, 0.5, -0.5, 0.25, -0.25, 0, 0, 0, 0, 0, 0, 0, 0])
        float[] real = {1, 0, -1, 0, 0.5f, -0.5f, 0.25f, -0.25f, 0, 0, 0, 0, 0, 0, 0, 0};
        float[] imag = new float[16];
        Fft.forward(real, imag);
        // bin 0: sum of all = 1 + 0 + (-1) + 0 + 0.5 + (-0.5) + 0.25 + (-0.25) = 0
        assertThat(real[0]).isCloseTo(0f, within(1e-5f));
        assertThat(imag[0]).isCloseTo(0f, within(1e-5f));
    }

    @Test void nextPowerOfTwo() {
        assertThat(Fft.nextPowerOfTwo(1)).isEqualTo(1);
        assertThat(Fft.nextPowerOfTwo(2)).isEqualTo(2);
        assertThat(Fft.nextPowerOfTwo(3)).isEqualTo(4);
        assertThat(Fft.nextPowerOfTwo(5)).isEqualTo(8);
        assertThat(Fft.nextPowerOfTwo(7)).isEqualTo(8);
        assertThat(Fft.nextPowerOfTwo(8)).isEqualTo(8);
        assertThat(Fft.nextPowerOfTwo(9)).isEqualTo(16);
        assertThat(Fft.nextPowerOfTwo(400)).isEqualTo(512);
        assertThat(Fft.nextPowerOfTwo(1024)).isEqualTo(1024);
    }

    @Test void nonPowerOfTwoThrows() {
        float[] real = {1, 2, 3};
        float[] imag = new float[3];
        assertThatThrownBy(() -> Fft.forward(real, imag))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void mismatchedLengthsThrow() {
        float[] real = new float[8];
        float[] imag = new float[4];
        assertThatThrownBy(() -> Fft.forward(real, imag))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void parsevalsTheorem() {
        float[] real = {0.3f, -1.2f, 0.7f, 2.1f, -0.5f, 0.9f, -0.1f, 1.4f};
        float[] imag = new float[8];
        double timeDomainEnergy = 0;
        for (float v : real) timeDomainEnergy += v * v;

        float[] freqReal = real.clone();
        float[] freqImag = imag.clone();
        Fft.forward(freqReal, freqImag);
        double freqDomainEnergy = 0;
        for (int i = 0; i < 8; i++) {
            freqDomainEnergy += freqReal[i] * freqReal[i] + freqImag[i] * freqImag[i];
        }
        freqDomainEnergy /= 8;

        assertThat(freqDomainEnergy).isCloseTo(timeDomainEnergy, within(1e-4));
    }
}
