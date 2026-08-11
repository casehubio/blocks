package io.casehub.blocks.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.blocks.attestation.AttestationIntentWriter;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VouchServiceTest {

    private final AttestationIntentWriter writer = mock(AttestationIntentWriter.class);

    private VouchRequest request() {
        return new VouchRequest("voucher-1", UUID.randomUUID(), "pr-review",
                "tenant-1", ActorType.AGENT, "senior-dev", null, Map.of());
    }

    @Test
    void allConstraintsPassWritesEndorsedAttestation() {
        var service = new VouchService(List.of(r -> new VouchEligibility.Eligible()), writer);
        var req = request();
        var result = service.vouch(req);

        assertThat(result).isInstanceOf(VouchResult.Accepted.class);
        var accepted = (VouchResult.Accepted) result;
        assertThat(accepted.attestationEntryId()).isNotNull();

        var captor = ArgumentCaptor.forClass(AttestationIntent.class);
        verify(writer).write(captor.capture(), eq("tenant-1"));
        var intent = captor.getValue();
        assertThat(intent.verdict()).isEqualTo(AttestationVerdict.ENDORSED);
        assertThat(intent.confidence()).isEqualTo(1.0);
        assertThat(intent.attestorId()).isEqualTo("voucher-1");
        assertThat(intent.subjectId()).isEqualTo(req.voucheeId());
        assertThat(intent.capabilityTag()).isEqualTo("pr-review");
        assertThat(intent.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(intent.attestorRole()).isEqualTo("senior-dev");
        assertThat(intent.dimensions()).isEmpty();
        assertThat(intent.evidence()).isEqualTo("vouch");
        assertThat(intent.entryId()).isEqualTo(accepted.attestationEntryId());
    }

    @Test
    void anyConstraintFailsReturnsRejectedWithAllReasons() {
        var constraints = List.<VouchConstraint>of(
                r -> new VouchEligibility.Ineligible("trust too low"),
                r -> new VouchEligibility.Eligible(),
                r -> new VouchEligibility.Ineligible("capacity exceeded"));
        var service = new VouchService(constraints, writer);
        var result = service.vouch(request());

        assertThat(result).isInstanceOf(VouchResult.Rejected.class);
        var rejected = (VouchResult.Rejected) result;
        assertThat(rejected.reasons()).containsExactly("trust too low", "capacity exceeded");
        verify(writer, never()).write(any(), any());
    }

    @Test
    void noConstraintsAlwaysPasses() {
        var service = new VouchService(List.of(), writer);
        var result = service.vouch(request());

        assertThat(result).isInstanceOf(VouchResult.Accepted.class);
        verify(writer).write(any(), eq("tenant-1"));
    }

    @Test
    void writerExceptionPropagates() {
        doThrow(new RuntimeException("db down"))
                .when(writer).write(any(), any());
        var service = new VouchService(List.of(), writer);

        assertThatThrownBy(() -> service.vouch(request()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
    }
}
