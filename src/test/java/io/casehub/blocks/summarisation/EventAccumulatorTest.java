package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class EventAccumulatorTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    @Test
    void shouldEmit_timestampTrigger_firesWhenMaxAgeExceeded() {
        var acc = new EventAccumulator<String>(new WindowPolicy(100, 0));
        acc.collect(new LevelEvent<>("a", 10, LEVEL));
        assertThat(acc.shouldEmit(50)).isFalse();
        assertThat(acc.shouldEmit(111)).isTrue();
    }

    @Test
    void shouldEmit_countTrigger_firesWhenMaxCountReached() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 3));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        assertThat(acc.shouldEmit(999)).isFalse();
        acc.collect(new LevelEvent<>("c", 3, LEVEL));
        assertThat(acc.shouldEmit(999)).isTrue();
    }

    @Test
    void shouldEmit_dualTrigger_firesOnEitherCondition() {
        var acc = new EventAccumulator<String>(new WindowPolicy(100, 5));
        acc.collect(new LevelEvent<>("a", 10, LEVEL));
        assertThat(acc.shouldEmit(111)).as("timestamp trigger").isTrue();

        var acc2 = new EventAccumulator<String>(new WindowPolicy(100, 2));
        acc2.collect(new LevelEvent<>("a", 10, LEVEL));
        acc2.collect(new LevelEvent<>("b", 11, LEVEL));
        assertThat(acc2.shouldEmit(12)).as("count trigger").isTrue();
    }

    @Test
    void drain_returnsAndClears() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 1));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        var drained = acc.drain();
        assertThat(drained).hasSize(2);
        assertThat(acc.size()).isZero();
        assertThat(acc.shouldEmit(999)).isFalse();
    }

    @Test
    void clear_resetsAllState() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 1));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.clear();
        assertThat(acc.size()).isZero();
        assertThat(acc.drain()).isEmpty();
    }

    @Test
    void shouldEmit_emptyAccumulator_neverFires() {
        var acc = new EventAccumulator<String>(new WindowPolicy(100, 5));
        assertThat(acc.shouldEmit(999)).isFalse();
    }

    // --- Boundary tests ---

    @Test
    void shouldEmit_countBoundary_exactlyAtThreshold() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 3));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        assertThat(acc.shouldEmit(999)).as("below threshold").isFalse();
        acc.collect(new LevelEvent<>("c", 3, LEVEL));
        assertThat(acc.shouldEmit(999)).as("exactly at threshold").isTrue();
    }

    @Test
    void shouldEmit_ageBoundary_exactlyAtMaxAge() {
        var acc = new EventAccumulator<String>(new WindowPolicy(100, 0));
        acc.collect(new LevelEvent<>("a", 10, LEVEL));
        assertThat(acc.shouldEmit(109)).as("1ms below maxAge").isFalse();
        assertThat(acc.shouldEmit(110)).as("exactly at maxAge").isTrue();
    }

    @Test
    void shouldEmit_singleEvent_countOfOne() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 1));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        assertThat(acc.shouldEmit(1)).isTrue();
    }

    // --- Combinatorial branches ---

    @Test
    void shouldEmit_dualPolicy_neitherTriggers() {
        var acc = new EventAccumulator<String>(new WindowPolicy(100, 5));
        acc.collect(new LevelEvent<>("a", 10, LEVEL));
        acc.collect(new LevelEvent<>("b", 11, LEVEL));
        assertThat(acc.shouldEmit(50)).as("2 events < maxCount=5, age 40 < maxAge=100").isFalse();
    }

    @Test
    void shouldEmit_countOnlyPolicy_belowThreshold() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 3));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        assertThat(acc.shouldEmit(999_999)).as("maxAge=0 disabled, 2 < maxCount=3").isFalse();
    }

    // --- Edge cases ---

    @Test
    void drain_returnsImmutableCopy() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 1));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        var drained = acc.drain();
        assertThat(drained).hasSize(1);
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        assertThat(drained).as("drained list unaffected by subsequent collect").hasSize(1);
    }

    @Test
    void drain_emptyAccumulator_returnsEmptyList() {
        var acc = new EventAccumulator<String>(new WindowPolicy(100, 0));
        var drained = acc.drain();
        assertThat(drained).isEmpty();
    }

    @Test
    void size_tracksCollectsAndDrains() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 10));
        assertThat(acc.size()).isZero();
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        assertThat(acc.size()).isEqualTo(2);
        acc.drain();
        assertThat(acc.size()).isZero();
    }

    @Test
    void shouldEmit_afterDrain_requiresNewEvents() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 2));
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        assertThat(acc.shouldEmit(999)).isTrue();
        acc.drain();
        assertThat(acc.shouldEmit(999)).as("empty after drain").isFalse();
        acc.collect(new LevelEvent<>("c", 3, LEVEL));
        assertThat(acc.shouldEmit(999)).as("one event, need two").isFalse();
    }

    // --- Thread-safety ---

    @Test
    void concurrentCollectAndDrain_noDataLoss() throws Exception {
        final int eventCount = 1000;
        var acc = new EventAccumulator<Integer>(new WindowPolicy(0, eventCount + 1));
        var latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<LevelEvent<Integer>> allDrained = new ArrayList<>();

            for (int i = 0; i < eventCount; i++) {
                final int val = i;
                executor.submit(() -> {
                    try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    acc.collect(new LevelEvent<>(val, val, LEVEL));
                });
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            allDrained.addAll(acc.drain());
            assertThat(allDrained).as("all events collected without loss").hasSize(eventCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCollectAndShouldEmit_noException() throws Exception {
        var acc = new EventAccumulator<Integer>(new WindowPolicy(0, 50));
        var latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 200; i++) {
                final int val = i;
                executor.submit(() -> {
                    try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    acc.collect(new LevelEvent<>(val, val, LEVEL));
                    acc.shouldEmit(val);
                });
            }

            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void drainIfReady_returnsEventsWhenReady() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 2));
        acc.collect(new LevelEvent<>("a", 1, new EventLevel("L1", 0)));
        acc.collect(new LevelEvent<>("b", 2, new EventLevel("L1", 0)));

        var result = acc.drainIfReady(5);
        assertThat(result).hasSize(2);
        assertThat(acc.size()).isZero();
    }

    @Test
    void drainIfReady_returnsEmptyWhenNotReady() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 5));
        acc.collect(new LevelEvent<>("a", 1, new EventLevel("L1", 0)));

        var result = acc.drainIfReady(2);
        assertThat(result).isEmpty();
        assertThat(acc.size()).as("buffer untouched").isEqualTo(1);
    }

    @Test
    void drainIfReady_emptyBuffer_returnsEmpty() {
        var acc = new EventAccumulator<String>(new WindowPolicy(0, 1));

        var result = acc.drainIfReady(100);
        assertThat(result).isEmpty();
    }


}
