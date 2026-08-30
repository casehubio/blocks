package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

interface TtsDecoder {
    float[] decode(GeneratorOutput generatorOutput, @Nullable VoiceData voiceData);
}
