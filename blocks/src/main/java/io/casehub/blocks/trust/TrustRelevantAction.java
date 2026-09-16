package io.casehub.blocks.trust;

import java.util.List;
import java.util.Objects;

public record TrustRelevantAction(
    String actorId,
    String targetId,
    String actionType,
    String actionResult,
    List<String> witnessIds,
    String tenantId
) {
    public TrustRelevantAction {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(actionType);
        witnessIds = witnessIds != null ? List.copyOf(witnessIds) : List.of();
        Objects.requireNonNull(tenantId);
    }
}
