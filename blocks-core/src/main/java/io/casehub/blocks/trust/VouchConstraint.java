package io.casehub.blocks.trust;

public interface VouchConstraint {
    VouchEligibility check(VouchRequest request);
}
