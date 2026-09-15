package io.casehub.blocks.prompt;

@FunctionalInterface
public interface SystemPromptCustomiser {
    String customise(String baseSystemPrompt, String signatureId, String variantSlot);
}
