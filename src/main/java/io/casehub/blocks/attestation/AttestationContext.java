package io.casehub.blocks.attestation;

import java.util.Objects;
import java.util.UUID;

public record AttestationContext(
        String tenancyId,
        UUID caseId,
        String capabilityTag) {

    public AttestationContext {
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(capabilityTag, "capabilityTag");
    }
}
