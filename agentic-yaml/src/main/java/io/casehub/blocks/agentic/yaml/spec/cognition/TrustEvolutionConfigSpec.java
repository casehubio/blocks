package io.casehub.blocks.agentic.yaml.spec.cognition;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record TrustEvolutionConfigSpec(
    @Nullable List<TrustEventMappingSpec> events,
    @Nullable TrustScoringSpec scoring,
    @Nullable TrustConsolidationSpec consolidation,
    @Nullable TrustLevelSpec levels
) {
    public record TrustEventMappingSpec(
        String actionType,
        String verdict,
        double confidence,
        double witnessConfidence
    ) {}

    public record TrustScoringSpec(
        int decayHalfLifeDays,
        double negativeDecayMultiplier
    ) {}

    public record TrustConsolidationSpec(
        String overlayProperty,
        double significantChangeThreshold
    ) {}

    public record TrustLevelSpec(
        double high,
        double moderate,
        double low
    ) {}
}
