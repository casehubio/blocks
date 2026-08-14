package io.casehub.blocks.negotiation;

public record MajorityAcceptance() implements AcceptancePolicy {
    @Override
    public boolean isAccepted(NegotiationState state) {
        Proposal active = state.activeProposal();
        if (active == null) return false;
        long nonProposerCount = state.parties().stream()
                .filter(p -> !p.equals(active.proposer())).count();
        if (nonProposerCount == 0) return false;
        long acceptedCount = state.responses().values().stream()
                .filter(r -> r.decision() == PartyDecision.ACCEPTED).count();
        return acceptedCount > nonProposerCount / 2;
    }
}
