package io.casehub.blocks.memory;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpReflectionStore implements ReflectionStore {
    @Override
    public void store(ReflectionEntry entry) {}
}
