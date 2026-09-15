package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.LevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ObservationAccumulator<E> {

    private final ObservationRenderer<E> renderer;
    private final List<LevelEvent<E>> buffer = new ArrayList<>();
    private long lastDrainTimestamp;

    public ObservationAccumulator(ObservationRenderer<E> renderer) {
        this(renderer, System.currentTimeMillis());
    }

    ObservationAccumulator(ObservationRenderer<E> renderer, long createdAt) {
        this.renderer = renderer;
        this.lastDrainTimestamp = createdAt;
    }

    public synchronized void collect(LevelEvent<E> event) {
        buffer.add(event);
    }

    public CompletionStage<ObservationResult> drainObservation(long now) {
        List<LevelEvent<E>> snapshot;
        long timeSinceLastDrain;

        synchronized (this) {
            timeSinceLastDrain = now - lastDrainTimestamp;
            if (buffer.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ObservationResult.empty(timeSinceLastDrain));
            }
            snapshot = List.copyOf(buffer);
            buffer.clear();
            lastDrainTimestamp = now;
        }

        return renderer.render(snapshot,
                new ObservationContext(now, timeSinceLastDrain));
    }

    public synchronized int eventCount() {
        return buffer.size();
    }

    public synchronized void clear() {
        buffer.clear();
    }
}
