package io.casehub.blocks.negotiation;

@FunctionalInterface
public interface AcceptancePolicy {
    boolean isAccepted(NegotiationState state);
}
