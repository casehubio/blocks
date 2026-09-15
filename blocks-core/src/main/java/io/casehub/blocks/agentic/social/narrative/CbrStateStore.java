package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.summarisation.StateStore;
import org.jspecify.annotations.Nullable;

public class CbrStateStore implements StateStore<NarrativeState> {

    private final CbrNarrativeStore delegate;

    public CbrStateStore(CbrNarrativeStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable NarrativeState load(String partitionKey) {
        var parts = partitionKey.split(":", 2);
        return delegate.load(parts[0], parts[1]);
    }

    @Override
    public void store(String partitionKey, NarrativeState state) {
        delegate.store(state);
    }
}
