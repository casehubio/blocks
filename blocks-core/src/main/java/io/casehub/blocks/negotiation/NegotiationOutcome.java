package io.casehub.blocks.negotiation;

public enum NegotiationOutcome {
    PENDING,
    AGREED,
    DEADLOCKED,
    WITHDRAWN;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
