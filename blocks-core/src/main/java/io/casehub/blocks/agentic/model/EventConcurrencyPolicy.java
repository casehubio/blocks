package io.casehub.blocks.agentic.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public interface EventConcurrencyPolicy {

    List<DriverEvent> awaitEvents(BlockingQueue<DriverEvent> queue)
            throws InterruptedException;

    default EventConcurrencyPolicy then(EventConcurrencyPolicy next) {
        var first = this;
        return queue -> {
            var events = first.awaitEvents(queue);
            var staging = new LinkedBlockingQueue<DriverEvent>();
            staging.addAll(events);
            return next.awaitEvents(staging);
        };
    }

    static EventConcurrencyPolicy serialize() {
        return queue -> List.of(queue.take());
    }

    static EventConcurrencyPolicy coalesce() {
        return queue -> {
            var first = queue.take();
            var batch = new ArrayList<DriverEvent>();
            batch.add(first);
            queue.drainTo(batch);
            return batch;
        };
    }

    static EventConcurrencyPolicy coalesce(Duration window) {
        return queue -> {
            var first = queue.take();
            var batch = new ArrayList<DriverEvent>();
            batch.add(first);
            var deadline = System.nanoTime() + window.toNanos();
            while (System.nanoTime() < deadline) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                var next = queue.poll(remaining, TimeUnit.NANOSECONDS);
                if (next == null) break;
                batch.add(next);
            }
            return batch;
        };
    }

    static EventConcurrencyPolicy coalesceBySource() {
        return queue -> {
            var first = queue.take();
            var bySource = new LinkedHashMap<String, DriverEvent>();
            bySource.put(first.source(), first);
            var remaining = new ArrayList<DriverEvent>();
            queue.drainTo(remaining);
            for (var e : remaining) {
                bySource.put(e.source(), e);
            }
            return List.copyOf(bySource.values());
        };
    }
}
