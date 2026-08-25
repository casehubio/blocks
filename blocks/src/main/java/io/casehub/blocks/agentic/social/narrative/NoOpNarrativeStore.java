package io.casehub.blocks.agentic.social.narrative;

import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
public class NoOpNarrativeStore implements NarrativeStore {

    @Override
    public void store(NarrativeState state) {}

    @Override
    public @Nullable NarrativeState load(String scopeId, String tenantId) {
        return null;
    }
}
