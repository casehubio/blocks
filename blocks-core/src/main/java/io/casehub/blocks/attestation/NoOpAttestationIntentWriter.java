package io.casehub.blocks.attestation;

public class NoOpAttestationIntentWriter implements AttestationIntentWriter {

    @Override
    public void write(AttestationIntent intent, String tenancyId) {
    }
}
