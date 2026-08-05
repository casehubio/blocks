package io.casehub.blocks.trust;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record IntakeContext(
        String tenancyId,
        @Nullable String capabilityTag,
        Map<String, Object> attributes) {

    public IntakeContext {
        Objects.requireNonNull(tenancyId, "tenancyId");
        if (attributes == null) attributes = Map.of();
    }

    public IntakeContext(String tenancyId) {
        this(tenancyId, null, Map.of());
    }
}
