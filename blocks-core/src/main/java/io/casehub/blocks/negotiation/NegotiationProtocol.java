package io.casehub.blocks.negotiation;

public final class NegotiationProtocol {
    private NegotiationProtocol() {}

    public static final String OUTCOME_PENDING    = "PENDING";
    public static final String OUTCOME_AGREED     = "AGREED";
    public static final String OUTCOME_DEADLOCKED = "DEADLOCKED";
    public static final String OUTCOME_WITHDRAWN  = "WITHDRAWN";
}
