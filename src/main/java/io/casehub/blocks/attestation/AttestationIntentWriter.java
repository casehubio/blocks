package io.casehub.blocks.attestation;

public interface AttestationIntentWriter {
    void write(AttestationIntent intent, String tenancyId);
}
