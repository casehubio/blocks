package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;

public class NoOpNarrativeStore implements NarrativeStore {

    @Override
    public void store(NarrativeState state) {}

    @Override
    public @Nullable NarrativeState load(String scopeId, String tenantId) {
        return null;
    }
}
