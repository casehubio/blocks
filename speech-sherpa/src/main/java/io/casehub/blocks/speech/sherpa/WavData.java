package io.casehub.blocks.speech.sherpa;

import java.util.Objects;

public record WavData(float[] samples, int sampleRate, int channels) {
    public WavData {
        Objects.requireNonNull(samples, "samples");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        if (channels <= 0) throw new IllegalArgumentException("channels must be positive: " + channels);
    }
}
