package io.casehub.blocks.speech;

import java.util.List;

@FunctionalInterface
public interface PhonemeAligner {
    List<PhonemeTiming> align(String text, byte[] audioData, int sampleRate);
}
