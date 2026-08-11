package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationAccumulatorTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    ObservationRenderer<String> verbatimRenderer = (events, ctx) ->
            CompletableFuture.completedFuture(new ObservationResult(
                    "rendered:" + events.size(),
                    events.stream().map(e -> new ObservationChunk(
                            e.payload(), e.timestamp(),
                            ObservationTier.VERBATIM, 1, Map.of())).toList(),
                    events.size(),
                    ctx.timeSinceLastDrain(),
                    ObservationTier.VERBATIM));

    @Test
    void collectAndDrain_lifecycle() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL));
        acc.collect(new LevelEvent<>("b", 1200, LEVEL));
        assertThat(acc.eventCount()).isEqualTo(2);

        var result = acc.drainObservation(1500).toCompletableFuture().join();
        assertThat(result.eventCount()).isEqualTo(2);
        assertThat(result.renderedText()).isEqualTo("rendered:2");
        assertThat(acc.eventCount()).isZero();
    }

    @Test
    void emptyDrain_returnsEmptyResult() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        var result = acc.drainObservation(2000).toCompletableFuture().join();
        assertThat(result.eventCount()).isZero();
        assertThat(result.tier()).isNull();
        assertThat(result.renderedText()).isEmpty();
        assertThat(result.timeSinceLastDrain()).isEqualTo(1000);
    }

    @Test
    void emptyDrain_doesNotUpdateLastDrainTimestamp() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.drainObservation(2000).toCompletableFuture().join();
        acc.drainObservation(3000).toCompletableFuture().join();

        acc.collect(new LevelEvent<>("a", 3100, LEVEL));
        var result = acc.drainObservation(4000).toCompletableFuture().join();
        assertThat(result.timeSinceLastDrain())
                .as("time since creation, not since empty drains")
                .isEqualTo(3000);
    }

    @Test
    void firstDrain_timeSinceCreation() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL));
        var result = acc.drainObservation(1500).toCompletableFuture().join();
        assertThat(result.timeSinceLastDrain()).isEqualTo(500);
    }

    @Test
    void subsequentDrain_timeSinceLastNonEmptyDrain() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL));
        acc.drainObservation(2000).toCompletableFuture().join();

        acc.collect(new LevelEvent<>("b", 2500, LEVEL));
        var result = acc.drainObservation(3000).toCompletableFuture().join();
        assertThat(result.timeSinceLastDrain()).isEqualTo(1000);
    }

    @Test
    void clear_emptiesBuffer() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL));
        acc.collect(new LevelEvent<>("b", 1200, LEVEL));
        acc.clear();
        assertThat(acc.eventCount()).isZero();

        var result = acc.drainObservation(2000).toCompletableFuture().join();
        assertThat(result.eventCount()).isZero();
    }

    @Test
    void rendererFailure_propagatesViaCompletionStage() {
        ObservationRenderer<String> failingRenderer = (events, ctx) ->
                CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        var acc = new ObservationAccumulator<>(failingRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL));

        var stage = acc.drainObservation(2000);
        assertThatThrownBy(() -> stage.toCompletableFuture().join())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM timeout");

        assertThat(acc.eventCount()).as("buffer cleared before render").isZero();
    }

    @Test
    void concurrentCollectDuringDrain_noDataLoss() throws Exception {
        final int preCollect = 50;
        final int concurrentCollect = 200;
        var acc = new ObservationAccumulator<>(verbatimRenderer, 0);

        for (int i = 0; i < preCollect; i++) {
            acc.collect(new LevelEvent<>("pre-" + i, i, LEVEL));
        }

        var latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < concurrentCollect; i++) {
                final int val = i;
                executor.submit(() -> {
                    try { latch.await(); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    acc.collect(new LevelEvent<>("concurrent-" + val, 1000 + val, LEVEL));
                });
            }

            latch.countDown();
            var result = acc.drainObservation(5000).toCompletableFuture().join();

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            var result2 = acc.drainObservation(6000).toCompletableFuture().join();
            int total = result.eventCount() + result2.eventCount() + acc.eventCount();
            assertThat(total).isEqualTo(preCollect + concurrentCollect);
        } finally {
            executor.shutdownNow();
        }
    }
}
