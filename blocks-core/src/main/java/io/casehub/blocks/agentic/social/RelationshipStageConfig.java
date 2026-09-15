package io.casehub.blocks.agentic.social;

import java.util.List;
import java.util.Objects;

public record RelationshipStageConfig(
        List<StageTier> tiers,
        double decayRate,
        double positiveWeight,
        double negativeWeight) {

    public RelationshipStageConfig {
        Objects.requireNonNull(tiers, "tiers required");
        if (tiers.isEmpty()) throw new IllegalArgumentException("at least one tier required");
        tiers = List.copyOf(tiers);
        if (decayRate < 0.0 || decayRate > 1.0)
            throw new IllegalArgumentException("decayRate must be in [0,1], got " + decayRate);
        if (positiveWeight < 0.0)
            throw new IllegalArgumentException("positiveWeight must be >= 0, got " + positiveWeight);
        if (negativeWeight < 0.0)
            throw new IllegalArgumentException("negativeWeight must be >= 0, got " + negativeWeight);
    }

    public static RelationshipStageConfig defaults() {
        return new RelationshipStageConfig(
                List.of(
                        new StageTier("stranger", 0.0),
                        new StageTier("acquaintance", 0.2),
                        new StageTier("familiar", 0.4),
                        new StageTier("friend", 0.6),
                        new StageTier("confidant", 0.8)),
                0.01, 1.0, 0.5);
    }

    public String resolveStage(double familiarityScore) {
        String stage = tiers.get(0).name();
        for (var tier : tiers) {
            if (familiarityScore >= tier.threshold()) {
                stage = tier.name();
            }
        }
        return stage;
    }
}
