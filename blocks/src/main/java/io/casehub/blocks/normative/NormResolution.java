package io.casehub.blocks.normative;

import java.util.List;
import java.util.Objects;

public record NormResolution<T>(
        NormDecision<T> winner,
        List<NormDecision<T>> overridden,
        String reason,
        ResolutionMethod method
) {
    public NormResolution {
        Objects.requireNonNull(winner);
        overridden = List.copyOf(overridden);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(method);
    }
}
