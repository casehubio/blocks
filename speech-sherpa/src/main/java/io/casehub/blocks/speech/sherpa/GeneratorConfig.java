package io.casehub.blocks.speech.sherpa;

public record GeneratorConfig(float temperature, int topK, float topP,
                               int maxTokens, int minTokens) {

    public static GeneratorConfig defaults() {
        return new GeneratorConfig(1.0f, 25, 0.9f, 500, 10);
    }
}
