package io.casehub.blocks.trust;

import io.casehub.ledger.api.model.AttestationVerdict;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TrustEvolutionConfig(
    List<TrustEventMapping> events,
    ScoringConfig scoring,
    ConsolidationConfig consolidation,
    LevelConfig levels
) {
    public TrustEvolutionConfig {
        events = List.copyOf(Objects.requireNonNull(events));
        Objects.requireNonNull(scoring);
        Objects.requireNonNull(consolidation);
        Objects.requireNonNull(levels);
    }

    public Optional<TrustEventMapping> findMapping(String actionType) {
        return events.stream()
            .filter(m -> m.actionType().equals(actionType))
            .findFirst();
    }

    public record TrustEventMapping(
        String actionType,
        AttestationVerdict verdict,
        double confidence,
        double witnessConfidence
    ) {
        public TrustEventMapping {
            Objects.requireNonNull(actionType);
            Objects.requireNonNull(verdict);
        }
    }

    public record ScoringConfig(int decayHalfLifeDays, double negativeDecayMultiplier) {
        public ScoringConfig {
            if (decayHalfLifeDays <= 0) throw new IllegalArgumentException("decayHalfLifeDays must be positive");
            if (negativeDecayMultiplier <= 0) throw new IllegalArgumentException("negativeDecayMultiplier must be positive");
        }
    }

    public record ConsolidationConfig(String overlayProperty, double significantChangeThreshold) {
        public ConsolidationConfig {
            Objects.requireNonNull(overlayProperty);
            if (significantChangeThreshold < 0 || significantChangeThreshold > 1)
                throw new IllegalArgumentException("significantChangeThreshold must be in [0,1]");
        }
    }

    public record LevelConfig(double high, double moderate, double low) {
        public LevelConfig {
            if (high < moderate || moderate < low)
                throw new IllegalArgumentException("levels must be high >= moderate >= low");
        }
    }
}
