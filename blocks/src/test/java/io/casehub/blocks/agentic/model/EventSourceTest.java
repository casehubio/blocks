package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EventSourceTest {

    @Test
    void subscribeDeliversEventsToSink() {
        var received = new ArrayList<DriverEvent>();
        EventSource source = sink -> {
            sink.accept(DriverEvent.signal("test"));
            return EventSource.Cancellation.of(() -> {});
        };

        source.subscribe(received::add);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).source()).isEqualTo("test");
    }

    @Test
    void cancellationStopsDelivery() {
        var cancelled = new AtomicBoolean(false);
        EventSource source = sink -> EventSource.Cancellation.of(() -> cancelled.set(true));

        var cancellation = source.subscribe(e -> {});
        assertThat(cancelled.get()).isFalse();
        cancellation.cancel();
        assertThat(cancelled.get()).isTrue();
    }

    @Test
    void mergeDeliversFromAllSources() {
        var received = new ArrayList<DriverEvent>();
        EventSource a = sink -> {
            sink.accept(DriverEvent.signal("a"));
            return EventSource.Cancellation.of(() -> {});
        };
        EventSource b = sink -> {
            sink.accept(DriverEvent.signal("b"));
            return EventSource.Cancellation.of(() -> {});
        };

        EventSource.merge(a, b).subscribe(received::add);
        assertThat(received).extracting(DriverEvent::source)
            .containsExactly("a", "b");
    }

    @Test
    void mergeCancelsAllSources() {
        var cancelledA = new AtomicBoolean(false);
        var cancelledB = new AtomicBoolean(false);
        EventSource a = sink -> EventSource.Cancellation.of(() -> cancelledA.set(true));
        EventSource b = sink -> EventSource.Cancellation.of(() -> cancelledB.set(true));

        var cancellation = EventSource.merge(a, b).subscribe(e -> {});
        cancellation.cancel();
        assertThat(cancelledA.get()).isTrue();
        assertThat(cancelledB.get()).isTrue();
    }

    @Test
    void tickerEmitsPeriodicEvents() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        try {
            var received = new CopyOnWriteArrayList<DriverEvent>();
            var cancellation = EventSource.ticker(Duration.ofMillis(50), executor)
                .subscribe(received::add);

            await().atMost(Duration.ofSeconds(2))
                .until(() -> received.size() >= 3);

            cancellation.cancel();
            assertThat(received).allMatch(e -> "timer".equals(e.source()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void compositeCancellationCancelsAll() {
        var flags = new boolean[]{false, false, false};
        var cancellations = List.of(
            EventSource.Cancellation.of(() -> flags[0] = true),
            EventSource.Cancellation.of(() -> flags[1] = true),
            EventSource.Cancellation.of(() -> flags[2] = true)
        );

        EventSource.Cancellation.composite(cancellations).cancel();
        assertThat(flags).containsExactly(true, true, true);
    }
}
