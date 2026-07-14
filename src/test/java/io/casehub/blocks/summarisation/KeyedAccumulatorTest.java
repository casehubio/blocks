package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyedAccumulatorTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    // --- Basic grouping ---

    @Test
    void collect_groupsEventsByKey() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s.substring(0, 1), group -> false, 0);
        acc.collect(new LevelEvent<>("a1", 1, LEVEL));
        acc.collect(new LevelEvent<>("a2", 2, LEVEL));
        acc.collect(new LevelEvent<>("b1", 3, LEVEL));
        assertThat(acc.groupCount()).isEqualTo(2);
        assertThat(acc.eventCount()).isEqualTo(3);
    }

    @Test
    void drain_completedGroup_returnsSingleGroup() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(2);
        assertThat(groups.get(0).get(0).payload()).isEqualTo("k1");
    }

    @Test
    void drain_completedGroup_removesFromAccumulator() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        acc.drain(10);
        assertThat(acc.groupCount()).isZero();
        assertThat(acc.eventCount()).isZero();
    }

    @Test
    void drain_incompleteGroup_retained() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 3,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).isEmpty();
        assertThat(acc.groupCount()).isEqualTo(1);
    }

    // --- Staleness ---

    @Test
    void drain_staleGroup_emittedOnTimeout() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> false,
            100);
        acc.collect(new LevelEvent<>("k1", 10, LEVEL));
        assertThat(acc.drain(50)).as("not stale yet").isEmpty();
        var groups = acc.drain(111);
        assertThat(groups).as("stale after 100ms of inactivity").hasSize(1);
        assertThat(groups.get(0)).hasSize(1);
    }

    @Test
    void drain_staleTimeout_resetsOnNewEvent() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> false,
            100);
        acc.collect(new LevelEvent<>("k1", 10, LEVEL));
        assertThat(acc.drain(80)).as("not stale at t=80").isEmpty();
        acc.collect(new LevelEvent<>("k1", 90, LEVEL));
        assertThat(acc.drain(150)).as("timer reset at t=90, not stale at t=150").isEmpty();
        var groups = acc.drain(191);
        assertThat(groups).as("stale at t=191 (90+100+1)").hasSize(1);
        assertThat(groups.get(0)).hasSize(2);
    }

    @Test
    void drain_zeroStaleTimeout_neverForcesEmission() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> false,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        assertThat(acc.drain(999_999)).isEmpty();
        assertThat(acc.groupCount()).isEqualTo(1);
    }

    // --- Mixed completed + stale ---

    @Test
    void drain_completedAndStale_bothReturned() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s.substring(0, 2),
            group -> group.stream().anyMatch(e -> e.payload().endsWith("!")),
            100);
        acc.collect(new LevelEvent<>("k1-start", 10, LEVEL));
        acc.collect(new LevelEvent<>("k1!", 20, LEVEL));
        acc.collect(new LevelEvent<>("k2-start", 10, LEVEL));
        var groups = acc.drain(200);
        assertThat(groups).hasSize(2);
    }

    // --- Multi-key interleaving ---

    @Test
    void drain_multipleKeys_independentCompletion() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s.substring(0, 1),
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("a1", 1, LEVEL));
        acc.collect(new LevelEvent<>("b1", 2, LEVEL));
        acc.collect(new LevelEvent<>("a2", 3, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).as("only group 'a' completed").hasSize(1);
        assertThat(groups.get(0)).extracting(e -> e.payload()).containsExactly("a1", "a2");
        assertThat(acc.groupCount()).as("group 'b' still active").isEqualTo(1);
    }

    // --- Edge cases ---

    @Test
    void drain_emptyAccumulator_returnsEmptyList() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s, group -> true, 0);
        assertThat(acc.drain(100)).isEmpty();
    }

    @Test
    void drain_singleEventGroup_completesImmediately() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> true,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(1);
    }

    @Test
    void clear_removesAllGroups() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s, group -> false, 0);
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        acc.clear();
        assertThat(acc.groupCount()).isZero();
        assertThat(acc.eventCount()).isZero();
        assertThat(acc.drain(999)).isEmpty();
    }

    @Test
    void collect_nullKeyFromExtractor_throwsNPE() {
        var acc = new KeyedAccumulator<String, String>(
            s -> null, group -> false, 0);
        assertThatThrownBy(() -> acc.collect(new LevelEvent<>("a", 1, LEVEL)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void drain_completionCheckedPerDrain_notOnCollect() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        assertThat(acc.groupCount()).as("group still present before drain").isEqualTo(1);
        acc.drain(10);
        assertThat(acc.groupCount()).as("removed after drain").isZero();
    }

    // --- Boundary ---

    @Test
    void drain_staleBoundary_exactlyAtTimeout() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s, group -> false, 100);
        acc.collect(new LevelEvent<>("k1", 10, LEVEL));
        assertThat(acc.drain(109)).as("1ms below timeout").isEmpty();
        var groups = acc.drain(110);
        assertThat(groups).as("exactly at timeout").hasSize(1);
    }

    // --- Thread-safety ---

    @Test
    void concurrentCollectAndDrain_noDataLoss() throws Exception {
        final int eventCount = 500;
        var acc = new KeyedAccumulator<Integer, Integer>(
            i -> i % 10,
            group -> false,
            0);
        var latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
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
            assertThat(acc.eventCount()).isEqualTo(eventCount);
        } finally {
            executor.shutdownNow();
        }
    }
}
