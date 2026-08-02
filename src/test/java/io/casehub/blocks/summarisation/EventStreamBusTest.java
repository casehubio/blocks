package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStreamBusTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void publish_dispatchesToMatchingSubscribers() {
        var bus = new EventStreamBus<String>();
        List<String> received = new ArrayList<>();
        bus.subscribe(s -> s.startsWith("A"), e -> received.add(e.payload()));
        bus.publish(new LevelEvent<>("Alpha", 1, LEVEL));
        bus.publish(new LevelEvent<>("Beta", 2, LEVEL));
        bus.publish(new LevelEvent<>("Apex", 3, LEVEL));
        assertThat(received).containsExactly("Alpha", "Apex");
    }

    @Test
    void publish_multipleSubscribers_allReceive() {
        var bus = new EventStreamBus<String>();
        List<String> sub1 = new ArrayList<>();
        List<String> sub2 = new ArrayList<>();
        bus.subscribe(s -> true, e -> sub1.add(e.payload()));
        bus.subscribe(s -> true, e -> sub2.add(e.payload()));
        bus.publish(new LevelEvent<>("X", 1, LEVEL));
        assertThat(sub1).containsExactly("X");
        assertThat(sub2).containsExactly("X");
    }

    @Test
    void clearSubscriptions_removesAllSubscribers() {
        var          bus      = new EventStreamBus<String>();
        List<String> received = new ArrayList<>();
        bus.subscribe(s -> true, e -> received.add(e.payload()));
        bus.clearSubscriptions();
        bus.publish(new LevelEvent<>("X", 1, LEVEL));
        assertThat(received).isEmpty();
    }

    @Test
    void publish_noSubscribers_noError() {
        var bus = new EventStreamBus<String>();
        bus.publish(new LevelEvent<>("X", 1, LEVEL));
    }

    // --- Edge cases ---

    @Test
    void publish_predicateException_propagatesToCaller() {
        var bus = new EventStreamBus<String>();
        bus.subscribe(s -> { throw new RuntimeException("predicate failed"); }, e -> {});

        assertThatThrownBy(() -> bus.publish(new LevelEvent<>("X", 1, LEVEL)))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("predicate failed");
    }

    @Test
    void publish_callbackException_propagatesToCaller() {
        var bus = new EventStreamBus<String>();
        bus.subscribe(s -> true, e -> { throw new RuntimeException("callback failed"); });

        assertThatThrownBy(() -> bus.publish(new LevelEvent<>("X", 1, LEVEL)))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("callback failed");
    }

    @Test
    void publish_callbackException_preventsSubsequentSubscribers() {
        var bus = new EventStreamBus<String>();
        List<String> received = new ArrayList<>();
        bus.subscribe(s -> true, e -> { throw new RuntimeException("first fails"); });
        bus.subscribe(s -> true, e -> received.add(e.payload()));

        try { bus.publish(new LevelEvent<>("X", 1, LEVEL)); } catch (RuntimeException ignored) {}

        assertThat(received).as("second subscriber never called").isEmpty();
    }

    @Test
    void subscribe_duringPublish_visibleOnNextPublish() {
        var bus = new EventStreamBus<String>();
        List<String> lateReceived = new ArrayList<>();

        bus.subscribe(s -> true, e -> {
            bus.subscribe(s2 -> true, e2 -> lateReceived.add(e2.payload()));
        });

        bus.publish(new LevelEvent<>("first", 1, LEVEL));
        assertThat(lateReceived).as("late subscriber not called during current publish").isEmpty();

        bus.publish(new LevelEvent<>("second", 2, LEVEL));
        assertThat(lateReceived).as("late subscriber called on subsequent publish").containsExactly("second");
    }

    @Test
    void publish_predicateReceivesPayload_notLevelEvent() {
        var bus = new EventStreamBus<String>();
        List<String> predicateArgs = new ArrayList<>();
        bus.subscribe(s -> { predicateArgs.add(s); return true; }, e -> {});

        bus.publish(new LevelEvent<>("hello", 1, LEVEL));

        assertThat(predicateArgs).containsExactly("hello");
    }

    @Test
    void publish_preservesEventMetadata() {
        var bus = new EventStreamBus<String>();
        var level = new EventLevel("custom", 7);
        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        bus.publish(new LevelEvent<>("payload", 42, level));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).isEqualTo("payload");
        assertThat(received.get(0).timestamp()).isEqualTo(42);
        assertThat(received.get(0).level()).isEqualTo(level);
    }

    // --- Thread-safety ---

    @Test
    void concurrentPublishAndSubscribe_noException() throws Exception {
        var bus = new EventStreamBus<Integer>();
        var count = new AtomicInteger(0);
        var latch = new CountDownLatch(1);

        bus.subscribe(i -> true, e -> count.incrementAndGet());

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 100; i++) {
                final int val = i;
                executor.submit(() -> {
                    try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    bus.publish(new LevelEvent<>(val, val, LEVEL));
                });
                executor.submit(() -> {
                    try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    bus.subscribe(x -> true, e -> {});
                });
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            assertThat(count.get()).as("all publishes delivered to initial subscriber").isEqualTo(100);
        } finally {
            executor.shutdownNow();
        }
    }
}
