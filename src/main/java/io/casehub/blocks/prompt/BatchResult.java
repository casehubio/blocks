package io.casehub.blocks.prompt;

public sealed interface BatchResult {
    record AlreadyRunning(String signatureId) implements BatchResult {}
    record InsufficientData(int count, int minimum) implements BatchResult {}
    record NoImprovement(double controlScore, double candidateEstimate) implements BatchResult {}
    record VariantCreated(PromptVariant variant, String assignedSlot) implements BatchResult {}
    record VariantPromoted(PromptVariant promoted, PromptVariant demoted) implements BatchResult {}
}
