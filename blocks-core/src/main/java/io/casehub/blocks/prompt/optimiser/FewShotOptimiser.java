package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;
import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.OptimisationDataset;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.OptimiserResult;
import io.casehub.blocks.prompt.PromptOptimiser;
import io.casehub.blocks.prompt.PromptSignature;
import io.casehub.blocks.prompt.PromptVariant;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class FewShotOptimiser implements PromptOptimiser {

    private final DiversityStrategy diversityStrategy;

    public FewShotOptimiser() {
        this(new TopNDiversityStrategy());
    }

    public FewShotOptimiser(DiversityStrategy diversityStrategy) {
        this.diversityStrategy = diversityStrategy;
    }

    @Override
    public String id() {
        return "few-shot";
    }

    @Override
    public CompletionStage<OptimiserResult> optimise(
            PromptSignature signature,
            @Nullable PromptVariant currentVariant,
            OptimisationDataset dataset,
            OptimiserConfig config) {

        var shortlist = dataset.candidates().stream()
                .filter(c -> c.qualityScore() >= config.minQualityThreshold())
                .sorted(Comparator.comparingDouble(
                        (ExampleCandidate c) -> c.qualityScore() * c.similarityScore()).reversed())
                .limit(config.maxExamples() * 2L)
                .toList();

        var selected = diversityStrategy.select(shortlist, config.maxExamples());

        var examples = selected.stream()
                .map(c -> new FewShotExample(c.input(), c.output(), c.outcome(), c.qualityScore(), null))
                .toList();

        return CompletableFuture.completedFuture(new OptimiserResult(examples, null, 0.0));
    }
}
