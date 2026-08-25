package io.casehub.blocks.agentic.social.narrative;

import java.util.Objects;

public record NarrativeConfig(
        NarrativeSynthesisGate synthesisGate,
        int maxEpisodes,
        int maxThemes,
        double themeSalienceFloor,
        int maxReflectionsPerSynthesis,
        String memoryDomain,
        String caseType) {
    public NarrativeConfig {
        Objects.requireNonNull(synthesisGate);
        if (maxEpisodes < 1) {throw new IllegalArgumentException("maxEpisodes must be >= 1");}
        if (maxThemes < 1) {throw new IllegalArgumentException("maxThemes must be >= 1");}
        if (themeSalienceFloor < 0.0 || themeSalienceFloor > 1.0) {
            throw new IllegalArgumentException("themeSalienceFloor must be in [0, 1]");
        }
        if (maxReflectionsPerSynthesis < 1) {
            throw new IllegalArgumentException("maxReflectionsPerSynthesis must be >= 1");
        }
        Objects.requireNonNull(memoryDomain, "memoryDomain required");
        Objects.requireNonNull(caseType, "caseType required");
    }

    public static NarrativeConfig defaults() {
        return new NarrativeConfig(NarrativeSynthesisGate.defaults(), 50, 10, 0.1, 20,
                                   "narrative", "narrative");
    }
}
