package io.casehub.blocks.negotiation;

import java.util.LinkedHashSet;
import java.util.Set;

public record UnanimousAcceptance() implements AcceptancePolicy {
    @Override
    public boolean isAccepted(NegotiationState state) {
        Proposal active = state.activeProposal();
        if (active == null) return false;
        Set<String> respondersNeeded = new LinkedHashSet<>(state.parties());
        respondersNeeded.remove(active.proposer());
        if (respondersNeeded.isEmpty()) return false;
        return respondersNeeded.stream()
                .allMatch(p -> {
                    Response r = state.responses().get(p);
                    return r != null && r.decision() == PartyDecision.ACCEPTED;
                });
    }
}
