package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.attestation.AttestationContext;
import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.blocks.attestation.LifecycleAttestationObserver;

import java.util.List;

class TestAttestationObserver implements LifecycleAttestationObserver<String> {
    @Override
    public List<AttestationIntent> observe(String event, AttestationContext context) {
        return List.of();
    }
}
