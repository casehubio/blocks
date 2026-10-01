package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.ReflectionEntry;

public class NoOpReflectionStore implements ReflectionStore {
    @Override
    public void store(ReflectionEntry entry) {}
}
