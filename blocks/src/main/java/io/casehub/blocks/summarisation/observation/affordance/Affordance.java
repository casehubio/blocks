package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record Affordance(
        String actionType,
        @Nullable String label,
        @Nullable String requiredItem,
        List<String> acceptsItems) {

    public Affordance {
        if (actionType == null || actionType.isBlank())
            throw new IllegalArgumentException("actionType required");
        acceptsItems = List.copyOf(acceptsItems);
    }

    public Affordance(String actionType, @Nullable String label) {
        this(actionType, label, null, List.of());
    }
}
