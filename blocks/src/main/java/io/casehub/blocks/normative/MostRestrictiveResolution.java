package io.casehub.blocks.normative;

import io.casehub.api.spi.RiskDecision;
import io.casehub.api.spi.RiskDecision.GateRequired;
import io.casehub.api.spi.routing.CandidateSetStrategy;
import io.casehub.api.spi.routing.StaticSetStrategy;

import java.util.ArrayList;
import java.util.List;

public record MostRestrictiveResolution() implements ConflictResolutionStrategy<RiskDecision> {
    @Override
    public NormResolution<RiskDecision> resolve(List<NormDecision<RiskDecision>> conflicting) {
        if (conflicting.isEmpty()) {
            throw new IllegalArgumentException("Cannot resolve empty decision list");
        }
        if (conflicting.size() == 1) {
            return new NormResolution<>(conflicting.getFirst(), List.of(),
                    "Single decision — no conflict", ResolutionMethod.MOST_RESTRICTIVE);
        }

        NormDecision<RiskDecision> mostRestrictive = conflicting.getFirst();
        for (int i = 1; i < conflicting.size(); i++) {
            var candidate = conflicting.get(i);
            if (isMoreRestrictive(candidate.decision(), mostRestrictive.decision())) {
                mostRestrictive = candidate;
            }
        }
        var overridden = new ArrayList<>(conflicting);
        overridden.remove(mostRestrictive);
        return new NormResolution<>(mostRestrictive, overridden,
                "Most restrictive decision wins (source: " + mostRestrictive.source() + ")",
                ResolutionMethod.MOST_RESTRICTIVE);
    }

    private boolean isMoreRestrictive(RiskDecision candidate, RiskDecision current) {
        if (!(candidate instanceof GateRequired gc)) return false;
        if (!(current instanceof GateRequired gk)) return true;
        return isNarrower(gc, gk);
    }

    private boolean isNarrower(GateRequired a, GateRequired b) {
        boolean aHasQuorum = a.quorum() != null;
        boolean bHasQuorum = b.quorum() != null;
        if (aHasQuorum != bHasQuorum) return aHasQuorum;
        if (aHasQuorum) {
            if (a.quorum().required() != b.quorum().required())
                return a.quorum().required() > b.quorum().required();
            if (a.quorum().instances() != b.quorum().instances())
                return a.quorum().instances() < b.quorum().instances();
        }
        int sizeA = candidateSetSize(a.candidateGroups());
        int sizeB = candidateSetSize(b.candidateGroups());
        if (sizeA != sizeB) return sizeA < sizeB;
        if (a.expiresIn() != null && b.expiresIn() != null)
            return a.expiresIn().compareTo(b.expiresIn()) < 0;
        return a.expiresIn() != null;
    }

    private int candidateSetSize(CandidateSetStrategy strategy) {
        if (strategy == null) return Integer.MAX_VALUE;
        if (strategy instanceof StaticSetStrategy s) return s.values().size();
        return Integer.MAX_VALUE;
    }
}
