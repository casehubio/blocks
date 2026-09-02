package io.casehub.blocks.speech.sherpa;

import java.nio.file.Path;
import java.util.Objects;

public record KokoroConfig(Path modelDir, int voiceId, float lengthScale,
                           int numThreads, String provider) {
    public KokoroConfig {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads <= 0) {
            throw new IllegalArgumentException("numThreads must be positive: " + numThreads);
        }
        if (lengthScale <= 0) {
            throw new IllegalArgumentException("lengthScale must be positive: " + lengthScale);
        }
    }

    public static KokoroConfig defaults(Path modelDir) {
        return new KokoroConfig(modelDir, 0, 1.0f, 2, "cpu");
    }

    public static KokoroConfig defaults(Path modelDir, int voiceId) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        return new KokoroConfig(modelDir, voiceId, 1.0f, threads, "cpu");
    }
}
