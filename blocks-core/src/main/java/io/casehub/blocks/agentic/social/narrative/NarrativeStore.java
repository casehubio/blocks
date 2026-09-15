package io.casehub.blocks.agentic.social.narrative;

import org.jspecify.annotations.Nullable;

public interface NarrativeStore {
    void store(NarrativeState state);

    @Nullable NarrativeState load(String scopeId, String tenantId);
}
