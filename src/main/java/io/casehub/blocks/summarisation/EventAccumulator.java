package io.casehub.blocks.summarisation;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe event buffer. All public methods are synchronized — safe for
 * concurrent collect() (from async bus callbacks) and tick()-driven
 * shouldEmit()/drain() on the main thread.
 */
public class EventAccumulator<E> {

    private final WindowPolicy policy;
    private final List<LevelEvent<E>> buffer = new ArrayList<>();

    public EventAccumulator(WindowPolicy policy) {
        this.policy = policy;
    }

    public synchronized void collect(LevelEvent<E> event) {
        buffer.add(event);
    }

    public synchronized boolean shouldEmit(long now) {
        if (buffer.isEmpty()) return false;
        if (policy.maxCount() > 0 && buffer.size() >= policy.maxCount()) return true;
        if (policy.maxAge() > 0) {
            long oldest = buffer.get(0).timestamp();
            return (now - oldest) >= policy.maxAge();
        }
        return false;
    }

    public synchronized List<LevelEvent<E>> drain() {
        var result = List.copyOf(buffer);
        buffer.clear();
        return result;
    }

    public synchronized List<LevelEvent<E>> drainIfReady(long now) {
        if (!shouldEmit(now)) {return List.of();}
        return drain();
    }


    public synchronized void clear() {
        buffer.clear();
    }

    public synchronized int size() {
        return buffer.size();
    }
}
