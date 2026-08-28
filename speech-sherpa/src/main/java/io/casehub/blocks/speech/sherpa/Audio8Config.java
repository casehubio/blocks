package io.casehub.blocks.speech.sherpa;

import java.nio.file.Path;
import java.util.Objects;

public record Audio8Config(Path modelDir, String variant, int numThreads, String provider,
                           int maxTokens, float temperature, float topP, int topK) {
    public Audio8Config {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(provider, "provider");
        if (numThreads <= 0) throw new IllegalArgumentException("numThreads must be positive: " + numThreads);
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive: " + maxTokens);
    }

    public static Audio8Config defaults(Path modelDir) {
        return defaults(modelDir, "0.1b");
    }

    public static Audio8Config defaults(Path modelDir, String variant) {
        return new Audio8Config(modelDir, variant, 4, "cpu", 1024, 0.7f, 0.9f, 50);
    }
}
