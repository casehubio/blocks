package io.casehub.blocks.negotiation;

public record ThresholdAcceptance(int minAcceptances) implements AcceptancePolicy {
    public ThresholdAcceptance {
        if (minAcceptances < 1) throw new IllegalArgumentException("minAcceptances must be >= 1");
    }

    @Override
    public boolean isAccepted(NegotiationState state) {
        if (state.activeProposal() == null) return false;
        long acceptedCount = state.responses().values().stream()
                .filter(r -> r.decision() == PartyDecision.ACCEPTED).count();
        return acceptedCount >= minAcceptances;
    }
}
