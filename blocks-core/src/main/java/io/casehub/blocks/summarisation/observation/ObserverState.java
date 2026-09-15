package io.casehub.blocks.summarisation.observation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ObserverState<E, K> {

    private final ConcurrentHashMap<K, ObservationAccumulator<E>> accumulators
            = new ConcurrentHashMap<>();
    private final LinkedHashMap<K, RememberedPartition> rememberedCache
            = new LinkedHashMap<>();
    private final ObservationRenderer<E> renderer;

    public ObserverState(K initialPartition, ObservationRenderer<E> renderer) {
        this.renderer = renderer;
        accumulators.computeIfAbsent(initialPartition, k -> new ObservationAccumulator<>(renderer));
    }

    public ObservationAccumulator<E> accumulatorFor(K partition) {
        return accumulators.computeIfAbsent(partition, k -> new ObservationAccumulator<>(renderer));
    }

    public Map<K, ObservationAccumulator<E>> accumulators() {
        return accumulators;
    }

    public LinkedHashMap<K, RememberedPartition> rememberedCache() {
        return rememberedCache;
    }
}
