package io.casehub.blocks.speech.sherpa;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class WavWriter {

    private WavWriter() {}

    static byte[] encode(float[] samples, int sampleRate, int channels) {
        int        bitsPerSample = 16;
        int        dataSize      = samples.length * 2;
        int        fileSize      = 44 + dataSize;
        ByteBuffer buf           = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        buf.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        buf.putInt(fileSize - 8);
        buf.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');

        // fmt chunk
        buf.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        buf.putInt(16);
        buf.putShort((short) 1); // PCM
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * bitsPerSample / 8);
        buf.putShort((short) (channels * bitsPerSample / 8));
        buf.putShort((short) bitsPerSample);

        // data chunk
        buf.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        buf.putInt(dataSize);
        for (float sample : samples) {
            float clamped = Math.clamp(sample, -1.0f, 1.0f);
            buf.putShort((short) Math.clamp(Math.round(clamped * 32768), -32768, 32767));
        }

        return buf.array();
    }
}
