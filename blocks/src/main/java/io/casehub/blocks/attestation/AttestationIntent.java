package io.casehub.blocks.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public record AttestationIntent(
        UUID entryId,
        UUID subjectId,
        AttestationVerdict verdict,
        double confidence,
        String capabilityTag,
        String attestorId,
        ActorType actorType,
        String attestorRole,
        Map<String, Double> dimensions,
        String evidence,
        UUID namespace,
        @Nullable UUID causedByEntryId) {}
