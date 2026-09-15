package io.casehub.blocks.normative;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record RecencyResolution<T>() implements ConflictResolutionStrategy<T> {
    @Override
    public NormResolution<T> resolve(List<NormDecision<T>> conflicting) {
        if (conflicting.isEmpty()) throw new IllegalArgumentException("Cannot resolve empty decision list");
        if (conflicting.size() == 1) return new NormResolution<>(conflicting.getFirst(), List.of(), "Single decision — no conflict", ResolutionMethod.RECENCY);
        var sorted = new ArrayList<>(conflicting);
        sorted.sort(Comparator.comparing(NormDecision<T>::establishedAt).reversed());
        var winner = sorted.getFirst();
        var overridden = sorted.subList(1, sorted.size());
        return new NormResolution<>(winner, overridden,
                "Most recent norm wins (source: " + winner.source() + ", established: " + winner.establishedAt() + ")",
                ResolutionMethod.RECENCY);
    }
}
