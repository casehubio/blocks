package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record ObservableEntity(
        String id,
        String displayName,
        @Nullable String description,
        List<Affordance> affordances) {

    public ObservableEntity {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id required");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName required");
        affordances = List.copyOf(affordances);
    }

    public ObservableEntity(String id, String displayName,
                            @Nullable String description) {
        this(id, displayName, description, List.of());
    }
}
