package io.casehub.blocks.summarisation.observation;

import java.util.Map;
import java.util.Set;

@FunctionalInterface
public interface VisibilityPolicy<E, K> {
    Map<String, Set<K>> resolve(E event);
}
