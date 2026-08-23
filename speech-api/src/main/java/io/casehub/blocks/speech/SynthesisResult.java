package io.casehub.blocks.speech;

import java.util.List;
import java.util.Objects;

public record SynthesisResult(byte[] audioData, String audioFormat, List<PhonemeTiming> phonemes) {
    public SynthesisResult {
        Objects.requireNonNull(audioData, "audioData");
        Objects.requireNonNull(audioFormat, "audioFormat");
        phonemes = phonemes != null ? List.copyOf(phonemes) : List.of();
    }
}
