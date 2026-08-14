package io.casehub.blocks.negotiation;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record NegotiationState(
        List<Proposal> proposals,
        Set<String> parties,
        Map<String, Response> responses,
        NegotiationOutcome outcome
) {
    public NegotiationState {
        proposals = List.copyOf(proposals);
        parties = Set.copyOf(parties);
        responses = Map.copyOf(responses);
        Objects.requireNonNull(outcome);
    }

    public @Nullable Proposal activeProposal() {
        for (int i = proposals.size() - 1; i >= 0; i--) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) return proposals.get(i);
        }
        return null;
    }

    public int round() {
        return proposals.size();
    }

    public boolean hasActiveProposal() {
        return activeProposal() != null;
    }
}
