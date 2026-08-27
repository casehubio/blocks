package io.casehub.blocks.speech.ws;

public record AssembledPrompt(String systemPrompt, String userPrompt, @org.jspecify.annotations.Nullable String model) {
    public AssembledPrompt(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, null);
    }
}
