package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WavWriterTest {

    @Test
    void roundTripsMonoSamples() throws IOException {
        float[] original = {0.5f, -0.5f, 0.0f, 1.0f, -1.0f};
        byte[] wav = WavWriter.encode(original, 16000, 1);

        WavData parsed = WavReader.parse(wav);
        assertThat(parsed.sampleRate()).isEqualTo(16000);
        assertThat(parsed.channels()).isEqualTo(1);
        assertThat(parsed.samples()).hasSize(5);
        for (int i = 0; i < original.length; i++) {
            assertThat(parsed.samples()[i]).isCloseTo(original[i], within(1f / 32768f));
        }
    }

    @Test
    void roundTripsStereoSamples() throws IOException {
        float[] original = {0.25f, -0.25f, 0.75f, -0.75f};
        byte[] wav = WavWriter.encode(original, 44100, 2);

        WavData parsed = WavReader.parse(wav);
        assertThat(parsed.sampleRate()).isEqualTo(44100);
        assertThat(parsed.channels()).isEqualTo(2);
        assertThat(parsed.samples()).hasSize(4);
    }

    @Test
    void encodesEmptySamples() throws IOException {
        byte[] wav = WavWriter.encode(new float[0], 16000, 1);

        WavData parsed = WavReader.parse(wav);
        assertThat(parsed.samples()).isEmpty();
    }

    @Test
    void clampsOutOfRangeSamples() throws IOException {
        float[] original = {2.0f, -2.0f};
        byte[] wav = WavWriter.encode(original, 16000, 1);

        WavData parsed = WavReader.parse(wav);
        assertThat(parsed.samples()[0]).isCloseTo(1.0f, within(1f / 32768f));
        assertThat(parsed.samples()[1]).isCloseTo(-1.0f, within(1f / 32768f));
    }

    @Test
    void producesValidWavHeader() {
        byte[] wav = WavWriter.encode(new float[]{0.1f, 0.2f}, 16000, 1);

        assertThat(wav[0]).isEqualTo((byte) 'R');
        assertThat(wav[1]).isEqualTo((byte) 'I');
        assertThat(wav[2]).isEqualTo((byte) 'F');
        assertThat(wav[3]).isEqualTo((byte) 'F');
        assertThat(wav[8]).isEqualTo((byte) 'W');
        assertThat(wav[9]).isEqualTo((byte) 'A');
        assertThat(wav[10]).isEqualTo((byte) 'V');
        assertThat(wav[11]).isEqualTo((byte) 'E');
        assertThat(wav.length).isEqualTo(44 + 4); // header + 2 samples * 2 bytes
    }
}
