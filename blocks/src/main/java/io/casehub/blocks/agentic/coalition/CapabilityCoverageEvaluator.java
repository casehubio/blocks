package io.casehub.blocks.agentic.coalition;

import java.util.LinkedHashSet;
import java.util.Set;

public record CapabilityCoverageEvaluator() implements CoalitionEvaluator {
    @Override
    public CoalitionScore evaluate(CoalitionProposal proposal, CoalitionContext context) {
        Set<String> required = proposal.requiredCapabilities();
        if (required.isEmpty()) {
            return new CoalitionScore(1.0, 1.0, Set.of(), "No capabilities required");
        }

        Set<String> covered = new LinkedHashSet<>();
        for (var member : proposal.proposedMembers()) {
            covered.addAll(context.capabilitiesOf(member));
        }
        covered.retainAll(required);

        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(covered);

        double coverage = (double) covered.size() / required.size();
        double score = missing.isEmpty() ? 1.0 : coverage * 0.8;

        String reason = missing.isEmpty()
                ? "All " + required.size() + " capabilities covered"
                : "Missing " + missing.size() + " of " + required.size() + ": " + missing;

        return new CoalitionScore(score, coverage, missing, reason);
    }
}
