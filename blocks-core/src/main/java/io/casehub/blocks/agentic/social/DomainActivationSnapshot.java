package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.index.CorrelationStrength;
import io.casehub.neocortex.cognitive.index.DomainCorrelation;
import io.casehub.neocortex.cognitive.index.DomainPair;
import io.casehub.neocortex.cognitive.index.DomainSignal;
import io.casehub.neocortex.cognitive.index.EventImpact;
import java.time.Instant;
import java.util.Map;

public record DomainActivationSnapshot(
        Map<DomainPair, DomainCorrelation> pairwiseCorrelations,
        Map<DomainPair, Map<String, DomainCorrelation>> moodCorrelations,
        Map<DomainPair, Map<String, EventImpact>> experienceImpacts,
        Map<String, DomainSignal> domainSignals,
        Map<String, String> subgraphNames,
        Instant computedAt
) {
    public boolean hasRenderableCorrelations() {
        return pairwiseCorrelations.values().stream()
                .anyMatch(c -> c.strength().ordinal() <= CorrelationStrength.MODERATE.ordinal());
    }
}
