package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class WavReaderTest {

    @TempDir Path tempDir;

    @Test
    void readsMono16kHz16BitPcm() throws IOException {
        short[] pcm = {100, 200, -100, 0, 32767};
        Path wav = writeWav(1, 16000, 16, pcm);

        WavData data = WavReader.read(wav);

        assertThat(data.sampleRate()).isEqualTo(16000);
        assertThat(data.channels()).isEqualTo(1);
        assertThat(data.samples()).hasSize(5);
        assertThat(data.samples()[0]).isCloseTo(100f / 32768f, within(1e-5f));
        assertThat(data.samples()[1]).isCloseTo(200f / 32768f, within(1e-5f));
        assertThat(data.samples()[2]).isCloseTo(-100f / 32768f, within(1e-5f));
        assertThat(data.samples()[4]).isCloseTo(32767f / 32768f, within(1e-5f));
    }

    @Test
    void readsStereo44100Hz() throws IOException {
        short[] pcm = {1000, -1000, 2000, -2000};
        Path wav = writeWav(2, 44100, 16, pcm);

        WavData data = WavReader.read(wav);

        assertThat(data.sampleRate()).isEqualTo(44100);
        assertThat(data.channels()).isEqualTo(2);
        assertThat(data.samples()).hasSize(4);
    }

    @Test
    void readsEmptyAudioData() throws IOException {
        Path wav = writeWav(1, 16000, 16, new short[0]);

        WavData data = WavReader.read(wav);

        assertThat(data.samples()).isEmpty();
    }

    @Test
    void rejectsNonWavFile() throws IOException {
        Path file = tempDir.resolve("notawav.bin");
        Files.write(file, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});

        assertThatThrownBy(() -> WavReader.read(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("RIFF");
    }

    @Test
    void rejectsNonPcmFormat() throws IOException {
        byte[] wav = buildWavBytes(1, 16000, 16, new short[]{100});
        wav[20] = 3; // audio format: non-PCM
        Path file = tempDir.resolve("nonpcm.wav");
        Files.write(file, wav);

        assertThatThrownBy(() -> WavReader.read(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PCM");
    }

    @Test
    void rejectsTruncatedFile() throws IOException {
        Path file = tempDir.resolve("truncated.wav");
        Files.write(file, new byte[]{'R', 'I', 'F', 'F'});

        assertThatThrownBy(() -> WavReader.read(file))
                .isInstanceOf(IOException.class);
    }

    private Path writeWav(int channels, int sampleRate, int bitsPerSample, short[] samples) throws IOException {
        byte[] data = buildWavBytes(channels, sampleRate, bitsPerSample, samples);
        Path file = tempDir.resolve("test.wav");
        Files.write(file, data);
        return file;
    }

    static byte[] buildWavBytes(int channels, int sampleRate, int bitsPerSample, short[] samples) {
        int dataSize = samples.length * (bitsPerSample / 8);
        int fileSize = 44 + dataSize;
        ByteBuffer buf = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        buf.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        buf.putInt(fileSize - 8);
        buf.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');

        // fmt chunk
        buf.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        buf.putInt(16); // PCM subchunk size
        buf.putShort((short) 1); // PCM format
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * bitsPerSample / 8); // byte rate
        buf.putShort((short) (channels * bitsPerSample / 8)); // block align
        buf.putShort((short) bitsPerSample);

        // data chunk
        buf.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        buf.putInt(dataSize);
        for (short s : samples) {
            buf.putShort(s);
        }

        return buf.array();
    }
}
