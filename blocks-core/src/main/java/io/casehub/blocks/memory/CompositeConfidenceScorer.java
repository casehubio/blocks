package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class CompositeConfidenceScorer implements ConfidenceScorer {

    private final List<WeightedScorer> scorers;

    public CompositeConfidenceScorer(List<WeightedScorer> scorers) {
        Objects.requireNonNull(scorers, "scorers required");
        if (scorers.isEmpty()) {
            throw new IllegalArgumentException("at least one scorer required");
        }
        this.scorers = List.copyOf(scorers);
    }

    @Override
    public double score(ScoredCbrCase<? extends CbrCase> memory, Instant now) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (var ws : scorers) {
            weightedSum += ws.scorer().score(memory, now) * ws.weight();
            totalWeight += ws.weight();
        }
        return Math.clamp(weightedSum / totalWeight, 0.0, 1.0);
    }
}
