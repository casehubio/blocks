package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class PartitionedObservationService<E, K> {

    private final ObservationRenderer<E> renderer;
    private final VisibilityPolicy<E, K> visibilityPolicy;
    private final Function<E, Long> timestampExtractor;
    private final Function<E, EventLevel> levelResolver;
    private final ConcurrentHashMap<String, ObserverState<E, K>> observers
            = new ConcurrentHashMap<>();

    public PartitionedObservationService(
            ObservationRenderer<E> renderer,
            VisibilityPolicy<E, K> visibilityPolicy,
            Function<E, Long> timestampExtractor,
            EventLevel eventLevel) {
        this(renderer, visibilityPolicy, timestampExtractor, e -> eventLevel);
    }

    public PartitionedObservationService(
            ObservationRenderer<E> renderer,
            VisibilityPolicy<E, K> visibilityPolicy,
            Function<E, Long> timestampExtractor,
            Function<E, EventLevel> levelResolver) {
        this.renderer = renderer;
        this.visibilityPolicy = visibilityPolicy;
        this.timestampExtractor = timestampExtractor;
        this.levelResolver = levelResolver;
    }

    public void addObserver(String observerId, K initialPartition) {
        observers.put(observerId, new ObserverState<>(initialPartition, renderer));
    }

    public void publishEvent(E event) {
        Map<String, Set<K>> routing = visibilityPolicy.resolve(event);
        long timestamp = timestampExtractor.apply(event);
        var levelEvent = new LevelEvent<>(event, timestamp, levelResolver.apply(event));

        for (var entry : routing.entrySet()) {
            String observerId = entry.getKey();
            ObserverState<E, K> state = observers.get(observerId);
            if (state == null) continue;
            for (K partition : entry.getValue()) {
                state.accumulatorFor(partition).collect(levelEvent);
            }
        }
    }

    public PartitionedDrain<K> drain(String observerId, K currentPartition, long now) {
        ObserverState<E, K> state = observers.get(observerId);
        if (state == null) {
            return new PartitionedDrain<>(ObservationResult.empty(0), Map.of());
        }

        ObservationResult currentResult = state.accumulatorFor(currentPartition)
                .drainObservation(now).toCompletableFuture().join();

        var remembered = new LinkedHashMap<K, RememberedPartition>();
        for (var accEntry : state.accumulators().entrySet()) {
            K partition = accEntry.getKey();
            if (partition.equals(currentPartition)) continue;

            var cached = state.rememberedCache().get(partition);
            if (cached != null) {
                remembered.put(partition, cached);
            } else {
                var result = accEntry.getValue()
                        .drainObservation(now).toCompletableFuture().join();
                if (result.eventCount() > 0) {
                    var rememberedPartition = new RememberedPartition(result, now);
                    state.rememberedCache().put(partition, rememberedPartition);
                    remembered.put(partition, rememberedPartition);
                }
            }
        }

        return new PartitionedDrain<>(currentResult, remembered);
    }

    public void clear() {
        observers.clear();
    }

    public ObserverState<E, K> observerState(String observerId) {
        return observers.get(observerId);
    }
}
