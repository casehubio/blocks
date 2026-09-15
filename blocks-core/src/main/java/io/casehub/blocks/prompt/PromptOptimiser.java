package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

public interface PromptOptimiser {
    String id();

    CompletionStage<OptimiserResult> optimise(
            PromptSignature signature,
            @Nullable PromptVariant currentVariant,
            OptimisationDataset dataset,
            OptimiserConfig config);
}
