package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.ReflectionEntry;

@FunctionalInterface
public interface ReflectionStore {
    void store(ReflectionEntry entry);
}
