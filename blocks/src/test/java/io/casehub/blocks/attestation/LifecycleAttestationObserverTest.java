package io.casehub.blocks.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleAttestationObserverTest {

    record TestEvent(String type, String subjectId) {}

    @Test
    void observerProducesIntentsFromEvent() {
        LifecycleAttestationObserver<TestEvent> observer = (event, ctx) -> List.of(
                new AttestationIntent(
                        UUID.randomUUID(), UUID.fromString(event.subjectId()),
                        AttestationVerdict.SOUND, 0.9, ctx.capabilityTag(),
                        "system", ActorType.AGENT, "observer",
                        Map.of("accuracy", 1.0), event.type(),
                        UUID.randomUUID(), null));

        var ctx = new AttestationContext("tenant-1", UUID.randomUUID(), "routing");
        var intents = observer.observe(new TestEvent("MERGED", "00000000-0000-0000-0000-000000000001"), ctx);

        assertThat(intents).hasSize(1);
        assertThat(intents.getFirst().verdict()).isEqualTo(AttestationVerdict.SOUND);
        assertThat(intents.getFirst().capabilityTag()).isEqualTo("routing");
        assertThat(intents.getFirst().evidence()).isEqualTo("MERGED");
    }

    @Test
    void observerCanReturnEmptyListForIrrelevantEvent() {
        LifecycleAttestationObserver<TestEvent> observer = (event, ctx) -> List.of();

        var ctx = new AttestationContext("tenant-1", UUID.randomUUID(), "routing");
        var intents = observer.observe(new TestEvent("IGNORED", "00000000-0000-0000-0000-000000000002"), ctx);

        assertThat(intents).isEmpty();
    }

    @Test
    void observerCanProduceMultipleIntentsFromSingleEvent() {
        LifecycleAttestationObserver<TestEvent> observer = (event, ctx) -> List.of(
                new AttestationIntent(UUID.randomUUID(), UUID.fromString(event.subjectId()),
                        AttestationVerdict.SOUND, 1.0, ctx.capabilityTag(),
                        "system", ActorType.AGENT, "observer",
                        Map.of("merge-rate", 1.0), "merged", UUID.randomUUID(), null),
                new AttestationIntent(UUID.randomUUID(), UUID.fromString(event.subjectId()),
                        AttestationVerdict.SOUND, 1.0, ctx.capabilityTag(),
                        "system", ActorType.AGENT, "observer",
                        Map.of("first-attempt-quality", 1.0), "merged", UUID.randomUUID(), null));

        var ctx = new AttestationContext("tenant-1", UUID.randomUUID(), "routing");
        var intents = observer.observe(new TestEvent("MERGED", "00000000-0000-0000-0000-000000000001"), ctx);

        assertThat(intents).hasSize(2);
    }
}
