package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

public record AssembledPrompt(String systemPrompt, String userPrompt, @Nullable String model) {
    public AssembledPrompt(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, null);
    }
}
