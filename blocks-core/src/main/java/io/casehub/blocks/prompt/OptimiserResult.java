package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record OptimiserResult(
        List<FewShotExample> examples,
        @Nullable String instructionDelta,
        double estimatedQuality) {

    public OptimiserResult {
        Objects.requireNonNull(examples, "examples");
        examples = List.copyOf(examples);
    }
}
