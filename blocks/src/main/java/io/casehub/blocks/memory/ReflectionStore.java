package io.casehub.blocks.memory;

@FunctionalInterface
public interface ReflectionStore {
    void store(ReflectionEntry entry);
}
