package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

final class WavReader {

    private WavReader() {}

    static WavData read(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    static WavData parse(byte[] data) throws IOException {
        if (data.length < 4
            || data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F') {
            throw new IOException("Not a WAV file: missing RIFF header");
        }
        if (data.length < 44) {
            throw new IOException("File too small to be a valid WAV (" + data.length + " bytes)");
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(4); // skip RIFF already checked
        buf.getInt(); // file size

        if (buf.get() != 'W' || buf.get() != 'A' || buf.get() != 'V' || buf.get() != 'E') {
            throw new IOException("Not a WAV file: missing WAVE marker");
        }

        // Find and parse fmt chunk
        int audioFormat   = -1;
        int channels      = -1;
        int sampleRate    = -1;
        int bitsPerSample = -1;

        while (buf.remaining() >= 8) {
            int chunkId   = buf.getInt();
            int chunkSize = buf.getInt();

            if (chunkId == chunkId("fmt ")) {
                if (chunkSize < 16) {throw new IOException("Invalid fmt chunk size: " + chunkSize);}
                audioFormat = Short.toUnsignedInt(buf.getShort());
                channels    = Short.toUnsignedInt(buf.getShort());
                sampleRate  = buf.getInt();
                buf.getInt(); // byte rate
                buf.getShort(); // block align
                bitsPerSample = Short.toUnsignedInt(buf.getShort());
                int extra = chunkSize - 16;
                if (extra > 0) {buf.position(buf.position() + extra);}
            } else if (chunkId == chunkId("data")) {
                if (audioFormat != 1) {
                    throw new IOException("Unsupported audio format (expected PCM/1, got " + audioFormat + ")");
                }
                if (bitsPerSample != 16) {
                    throw new IOException("Unsupported bits per sample: " + bitsPerSample + " (expected 16)");
                }
                int     sampleCount = chunkSize / 2;
                float[] samples     = new float[sampleCount];
                for (int i = 0; i < sampleCount; i++) {
                    samples[i] = buf.getShort() / 32768f;
                }
                return new WavData(samples, sampleRate, channels);
            } else {
                buf.position(buf.position() + chunkSize);
            }
        }

        if (audioFormat == -1) {throw new IOException("No fmt chunk found");}
        if (audioFormat != 1) {
            throw new IOException("Unsupported audio format (expected PCM/1, got " + audioFormat + ")");
        }
        return new WavData(new float[0], sampleRate, channels);
    }

    private static int chunkId(String id) {
        return ByteBuffer.wrap(id.getBytes()).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
