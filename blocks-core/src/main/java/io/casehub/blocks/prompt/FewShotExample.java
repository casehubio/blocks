package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record FewShotExample(
        String input,
        String output,
        String outcome,
        double qualityScore,
        @Nullable String annotation) {

    public FewShotExample {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(outcome, "outcome");
    }
}
