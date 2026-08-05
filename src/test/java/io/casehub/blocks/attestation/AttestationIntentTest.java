package io.casehub.blocks.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttestationIntentTest {

    @Test
    void causedByEntryIdIsNullable() {
        var intent = new AttestationIntent(
                UUID.randomUUID(), UUID.randomUUID(),
                AttestationVerdict.SOUND, 0.9, "routing",
                "actor-1", ActorType.AGENT, "analyst",
                Map.of("accuracy", 0.95), "evidence text",
                UUID.randomUUID(), null);
        assertThat(intent.causedByEntryId()).isNull();
    }

    @Test
    void causedByEntryIdIsCarriedThrough() {
        var causedBy = UUID.randomUUID();
        var intent = new AttestationIntent(
                UUID.randomUUID(), UUID.randomUUID(),
                AttestationVerdict.SOUND, 0.9, "routing",
                "actor-1", ActorType.AGENT, "analyst",
                Map.of(), "evidence",
                UUID.randomUUID(), causedBy);
        assertThat(intent.causedByEntryId()).isEqualTo(causedBy);
    }
}
