package io.casehub.blocks.speech.sherpa;

import java.nio.file.Path;
import java.util.Objects;

public record SherpaConfig(Path modelDir, int numThreads, String provider) {
    public SherpaConfig {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads <= 0) throw new IllegalArgumentException("numThreads must be positive: " + numThreads);
    }

    public static SherpaConfig defaults(Path modelDir) {
        return new SherpaConfig(modelDir, 2, "cpu");
    }
}
