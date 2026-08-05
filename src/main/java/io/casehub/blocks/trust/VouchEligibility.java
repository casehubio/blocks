package io.casehub.blocks.trust;

public sealed interface VouchEligibility {
    record Eligible() implements VouchEligibility {}
    record Ineligible(String reason) implements VouchEligibility {}
}
