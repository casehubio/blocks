package io.casehub.blocks.speech;

public interface SpeechDenoiser {
    float[] denoise(float[] samples, int sampleRate);
}
