package io.casehub.blocks.normative;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record SpecificityResolution<T>() implements ConflictResolutionStrategy<T> {
    @Override
    public NormResolution<T> resolve(List<NormDecision<T>> conflicting) {
        if (conflicting.isEmpty()) throw new IllegalArgumentException("Cannot resolve empty decision list");
        if (conflicting.size() == 1) return new NormResolution<>(conflicting.getFirst(), List.of(), "Single decision — no conflict", ResolutionMethod.SPECIFICITY);
        var sorted = new ArrayList<>(conflicting);
        sorted.sort(Comparator.comparingInt((NormDecision<T> d) -> d.specificity().ordinal()).reversed());
        var winner = sorted.getFirst();
        var overridden = sorted.subList(1, sorted.size());
        return new NormResolution<>(winner, overridden,
                "Specificity " + winner.specificity() + " wins (source: " + winner.source() + ")",
                ResolutionMethod.SPECIFICITY);
    }
}
