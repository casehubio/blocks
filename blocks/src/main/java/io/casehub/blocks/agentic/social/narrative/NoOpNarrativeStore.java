package io.casehub.blocks.agentic.social.narrative;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

@DefaultBean
@ApplicationScoped
public class NoOpNarrativeStore implements NarrativeStore {

    @Override
    public void store(NarrativeState state) {}

    @Override
    public @Nullable NarrativeState load(String scopeId, String tenantId) {
        return null;
    }
}
