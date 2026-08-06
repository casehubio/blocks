package io.casehub.blocks.attestation;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpAttestationIntentWriter implements AttestationIntentWriter {

    @Override
    public void write(AttestationIntent intent, String tenancyId) {
    }
}
