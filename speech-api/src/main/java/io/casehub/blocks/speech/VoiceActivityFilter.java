package io.casehub.blocks.speech;

public interface VoiceActivityFilter extends AutoCloseable {
    float[] filterChunk(float[] samples, int sampleRate);
    void reset();
    void close();
}
