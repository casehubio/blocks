package io.casehub.blocks.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.blocks.attestation.AttestationIntentWriter;
import io.casehub.ledger.api.model.AttestationVerdict;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VouchService {

    private final List<VouchConstraint> constraints;
    private final AttestationIntentWriter writer;

    public VouchService(List<VouchConstraint> constraints, AttestationIntentWriter writer) {
        this.constraints = List.copyOf(constraints);
        this.writer = writer;
    }

    public VouchResult vouch(VouchRequest request) {
        var reasons = constraints.stream()
                .map(c -> c.check(request))
                .filter(e -> e instanceof VouchEligibility.Ineligible)
                .map(e -> ((VouchEligibility.Ineligible) e).reason())
                .toList();

        if (!reasons.isEmpty()) {
            return new VouchResult.Rejected(reasons);
        }

        var entryId = UUID.randomUUID();
        var intent = new AttestationIntent(
                entryId,
                request.voucheeId(),
                AttestationVerdict.ENDORSED,
                1.0,
                request.capabilityTag(),
                request.voucherId(),
                request.voucherActorType(),
                request.voucherRole(),
                Map.of(),
                "vouch",
                request.namespace(),
                null);

        writer.write(intent, request.tenancyId());
        return new VouchResult.Accepted(entryId);
    }
}
