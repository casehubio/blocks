package io.casehub.blocks.trust;

import io.casehub.platform.api.identity.ActorType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record VouchRequest(
        String voucherId,
        UUID voucheeId,
        String capabilityTag,
        String tenancyId,
        ActorType voucherActorType,
        String voucherRole,
        @Nullable UUID namespace,
        Map<String, Object> attributes) {

    public VouchRequest {
        Objects.requireNonNull(voucherId, "voucherId");
        Objects.requireNonNull(voucheeId, "voucheeId");
        Objects.requireNonNull(capabilityTag, "capabilityTag");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(voucherActorType, "voucherActorType");
        Objects.requireNonNull(voucherRole, "voucherRole");
        if (attributes == null) attributes = Map.of();
    }
}
