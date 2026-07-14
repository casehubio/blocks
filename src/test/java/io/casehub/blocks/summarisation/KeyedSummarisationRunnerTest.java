package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyedSummarisationRunnerTest {

    private static final EventLevel INPUT_LEVEL = new EventLevel("input", 0);
    private static final EventLevel OUTPUT_LEVEL = new EventLevel("output", 1);

    @Test
    void tick_completedGroup_summariserCalledAndPublished() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s.substring(0, 1), group -> group.size() >= 2, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<LevelEvent<Integer>> received = new ArrayList<>();
        outputBus.subscribe(i -> true, received::add);

        runner.collect(new LevelEvent<>("a1", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("a2", 2, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).isEqualTo(2);
        assertThat(received.get(0).level()).isEqualTo(OUTPUT_LEVEL);
        assertThat(received.get(0).timestamp()).isEqualTo(10);
    }

    @Test
    void tick_noCompletedGroups_nothingPublished() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> group.size() >= 5, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("k1", 1, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).isEmpty();
    }

    @Test
    void tick_staleGroup_emittedAndSummarised() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> false, 100,
            summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("k1", 10, INPUT_LEVEL));
        runner.tick(50);
        assertThat(received).as("not stale yet").isEmpty();

        runner.tick(111);
        assertThat(received).as("stale group emitted").containsExactly(1);
    }

    @Test
    void tick_multipleCompletedGroups_eachSummarisedIndependently() {
        Summariser<String, String> summariser = Summariser.ofSync(
            batch -> List.of("group-" + batch.get(0).payload().substring(0, 1)
                + "-size-" + batch.size()));
        var outputBus = new EventStreamBus<String>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s.substring(0, 1), group -> group.size() >= 2, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<String> received = new ArrayList<>();
        outputBus.subscribe(s -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a1", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b1", 2, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("a2", 3, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b2", 4, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).containsExactlyInAnyOrder("group-a-size-2", "group-b-size-2");
    }

    @Test
    void tick_asyncFailure_propagatesThroughReturnedStage() {
        AtomicInteger callCount = new AtomicInteger();
        Summariser<String, Integer> failingSummariser = batch -> {
            callCount.incrementAndGet();
            return CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        };
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> group.size() >= 1, 0,
            failingSummariser, outputBus, OUTPUT_LEVEL);

        runner.collect(new LevelEvent<>("k1", 1, INPUT_LEVEL));
        CompletionStage<Void> result = runner.tick(10);

        assertThatThrownBy(() -> result.toCompletableFuture().join())
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessageContaining("LLM timeout");
    }

    @Test
    void tick_oneGroupFails_otherGroupStillPublished() {
        AtomicInteger callCount = new AtomicInteger();
        Summariser<String, Integer> summariser = batch -> {
            callCount.incrementAndGet();
            if (batch.get(0).payload().startsWith("fail")) {
                return CompletableFuture.failedFuture(new RuntimeException("boom"));
            }
            return CompletableFuture.completedFuture(List.of(batch.size()));
        };
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s.substring(0, 4), group -> group.size() >= 1, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("good", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("fail", 2, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).as("successful group published despite sibling failure").containsExactly(1);
    }

    @Test
    void tick_noGroups_returnsCompletedStage() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> true, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        CompletionStage<Void> result = runner.tick(100);
        assertThat(result.toCompletableFuture().isDone()).isTrue();
        assertThat(result.toCompletableFuture().isCompletedExceptionally()).isFalse();
    }

    @Test
    void clear_delegatesToAccumulator() {
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> false, 0,
            Summariser.ofSync(batch -> List.of()), new EventStreamBus<>(),
            OUTPUT_LEVEL);
        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b", 2, INPUT_LEVEL));
        runner.clear();
        assertThat(runner.groupCount()).isZero();
        assertThat(runner.eventCount()).isZero();
    }

    @Test
    void groupCountAndEventCount_delegateToAccumulator() {
        var runner = new KeyedSummarisationRunner<String, String, Object>(
            s -> s.substring(0, 1), group -> false, 0,
            Summariser.ofSync(batch -> List.of()), new EventStreamBus<>(),
            OUTPUT_LEVEL);
        runner.collect(new LevelEvent<>("a1", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("a2", 2, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b1", 3, INPUT_LEVEL));
        assertThat(runner.groupCount()).isEqualTo(2);
        assertThat(runner.eventCount()).isEqualTo(3);
    }
}
