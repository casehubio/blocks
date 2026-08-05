package io.casehub.blocks.attestation;

import java.util.List;

@FunctionalInterface
public interface LifecycleAttestationObserver<E> {
    List<AttestationIntent> observe(E event, AttestationContext context);
}
