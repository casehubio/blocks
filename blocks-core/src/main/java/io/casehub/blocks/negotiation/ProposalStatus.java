package io.casehub.blocks.negotiation;

public enum ProposalStatus {
    ACTIVE,
    SUPERSEDED,
    ACCEPTED,
    REJECTED;

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED;
    }
}
