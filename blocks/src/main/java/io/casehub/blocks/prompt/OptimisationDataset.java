package io.casehub.blocks.prompt;

import java.util.List;
import java.util.Objects;

public record OptimisationDataset(
        List<VariantOutcome> outcomes,
        List<ExampleCandidate> candidates) {

    public OptimisationDataset {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(candidates, "candidates");
        outcomes = List.copyOf(outcomes);
        candidates = List.copyOf(candidates);
    }
}
