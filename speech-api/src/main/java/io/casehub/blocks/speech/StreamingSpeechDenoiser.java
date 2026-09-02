package io.casehub.blocks.speech;

public interface StreamingSpeechDenoiser extends AutoCloseable {
    float[] processChunk(float[] samples, int sampleRate);
    void reset();
    void close();
}
