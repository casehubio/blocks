package io.casehub.blocks.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;

class NoOpAttestationIntentWriterTest {

    private final NoOpAttestationIntentWriter writer = new NoOpAttestationIntentWriter();

    @Test
    void writeIsNoOp() {
        var intent = new AttestationIntent(
                UUID.randomUUID(), UUID.randomUUID(),
                AttestationVerdict.SOUND, 0.9, "routing",
                "actor-1", ActorType.AGENT, "analyst",
                Map.of("accuracy", 0.95), "evidence text",
                UUID.randomUUID(), null);

        assertThatNoException().isThrownBy(() -> writer.write(intent, "tenant-1"));
    }

    @Test
    void implementsAttestationIntentWriter() {
        assertThatNoException().isThrownBy(() -> {
            AttestationIntentWriter spi = writer;
            spi.write(new AttestationIntent(
                    UUID.randomUUID(), UUID.randomUUID(),
                    AttestationVerdict.ENDORSED, 1.0, "vouch",
                    "voucher-1", ActorType.HUMAN, "reviewer",
                    Map.of(), "vouch", UUID.randomUUID(), null), "tenant-2");
        });
    }
}
