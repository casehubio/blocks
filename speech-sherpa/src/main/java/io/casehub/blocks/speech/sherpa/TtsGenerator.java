package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

interface TtsGenerator {
    GeneratorOutput generate(int[] tokens, @Nullable VoiceData voiceData,
                             GeneratorConfig config);
}
