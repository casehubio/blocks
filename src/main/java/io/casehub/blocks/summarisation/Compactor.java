package io.casehub.blocks.summarisation;

import java.util.List;

@FunctionalInterface
public interface Compactor<E> {
    List<LevelEvent<E>> compact(List<LevelEvent<E>> events);
}
