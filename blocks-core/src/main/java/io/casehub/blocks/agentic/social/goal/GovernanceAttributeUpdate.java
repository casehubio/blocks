package io.casehub.blocks.agentic.social.goal;

import java.util.Map;
import java.util.Objects;

public record GovernanceAttributeUpdate(
        String goalName,
        Map<String, String> attributeUpdates) {
    public GovernanceAttributeUpdate {
        Objects.requireNonNull(goalName);
        attributeUpdates = Map.copyOf(attributeUpdates);
    }
}
