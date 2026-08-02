package io.casehub.blocks.summarisation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Predicate-based pub/sub for typed events. Backed by {@link CopyOnWriteArrayList} —
 * concurrent {@code publish()} and {@code subscribe()} calls are safe. Thread-safety
 * of the overall pipeline depends on subscriber callbacks: when using
 * {@link SummarisationRunner}, the downstream {@link EventAccumulator}'s synchronized
 * methods ensure safe cross-thread access.
 */
public class EventStreamBus<E> {

    private record Subscription<E>(Predicate<E> filter, Consumer<LevelEvent<E>> callback) {}

    private final List<Subscription<E>> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * Registers a subscription. The callback runs synchronously on the
     * thread that calls {@link #publish} — blocking callbacks block
     * the entire publish loop. Wrap in {@code CompletableFuture.runAsync()}
     * if the callback may block.
     */
    public void subscribe(Predicate<E> filter, Consumer<LevelEvent<E>> callback) {
        subscriptions.add(new Subscription<>(filter, callback));
    }

    /**
     * Dispatches the event to all matching subscribers synchronously on
     * the calling thread. A slow or blocking callback delays all
     * subsequent subscribers in this publish call.
     */
    public void publish(LevelEvent<E> event) {
        for (var sub : subscriptions) {
            if (sub.filter().test(event.payload())) {
                sub.callback().accept(event);
            }
        }
    }

    public void clearSubscriptions() {
        subscriptions.clear();
    }

    @Deprecated(forRemoval = true)
    public void clear() {
        clearSubscriptions();
    }
}
