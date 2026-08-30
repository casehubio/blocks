package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface TtsVoiceEncoder {
    VoiceData encode(byte[] audioData, @Nullable String transcriptText);
}
