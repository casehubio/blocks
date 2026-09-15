package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

public record ActionDescriptor(
        String actionType,
        String description,
        @Nullable String parameterFormat) {

    public ActionDescriptor {
        if (actionType == null || actionType.isBlank())
            throw new IllegalArgumentException("actionType required");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description required");
    }
}
