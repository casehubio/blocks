package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.CleanupConfig;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

public record SherpaConfig(Path modelDir, int numThreads, String provider,
                           @Nullable Path punctuationModelDir,
                           @Nullable CleanupConfig cleanupConfig) {
    public SherpaConfig {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads <= 0) {throw new IllegalArgumentException("numThreads must be positive: " + numThreads);}
    }

    public SherpaConfig(Path modelDir, int numThreads, String provider) {
        this(modelDir, numThreads, provider, null, null);
    }

    public static SherpaConfig defaults(Path modelDir) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        return new SherpaConfig(modelDir, threads, "cpu", null, null);
    }

    public static SherpaConfig defaults() {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        return new SherpaConfig(Provisioner.defaultModelDir(), threads, "cpu", null, null);
    }

    public SherpaConfig withPunctuation(Path punctuationModelDir) {
        return new SherpaConfig(modelDir, numThreads, provider, punctuationModelDir, cleanupConfig);
    }

    public SherpaConfig withCleanup(CleanupConfig cleanupConfig) {
        return new SherpaConfig(modelDir, numThreads, provider, punctuationModelDir, cleanupConfig);
    }
}
