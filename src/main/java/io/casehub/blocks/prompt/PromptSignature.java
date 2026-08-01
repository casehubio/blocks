package io.casehub.blocks.prompt;

import java.util.Objects;

public record PromptSignature(
        String id,
        String description,
        String baseSystemPrompt,
        Class<?> inputType,
        Class<?> outputType) {

    public PromptSignature {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseSystemPrompt, "baseSystemPrompt");
    }
}
