package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PromptVariant(
        String signatureId,
        String variantId,
        List<FewShotExample> examples,
        @Nullable String instructionDelta,
        double qualityScore,
        Instant createdAt,
        @Nullable String parentVariantId,
        int consecutiveWins) {

    public PromptVariant {
        Objects.requireNonNull(signatureId, "signatureId");
        Objects.requireNonNull(variantId, "variantId");
        examples = List.copyOf(examples);
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
