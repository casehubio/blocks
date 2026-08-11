package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class SummarisationRunnerTest {

    private static final EventLevel INPUT_LEVEL = new EventLevel("input", 0);
    private static final EventLevel OUTPUT_LEVEL = new EventLevel("output", 1);

    @Test
    void tick_emitsWhenWindowMet_publishesToBus() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 2), summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.tick(5);
        assertThat(received).as("not enough events yet").isEmpty();

        runner.collect(new LevelEvent<>("b", 2, INPUT_LEVEL));
        runner.tick(5);
        assertThat(received).as("count threshold met").containsExactly(2);
    }

    @Test
    void tick_doesNotEmitWhenWindowNotMet() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(100, 0), summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 50, INPUT_LEVEL));
        runner.tick(60);
        assertThat(received).isEmpty();
    }

    @Test
    void tick_wrapsOutputInLevelEvent_withCorrectLevelAndTimestamp() {
        Summariser<String, String> summariser = Summariser.ofSync(batch -> List.of("summary"));
        var outputBus = new EventStreamBus<String>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1), summariser, outputBus, OUTPUT_LEVEL);

        List<LevelEvent<String>> received = new ArrayList<>();
        outputBus.subscribe(s -> true, received::add);

        runner.collect(new LevelEvent<>("a", 10, INPUT_LEVEL));
        runner.tick(42);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).level()).isEqualTo(OUTPUT_LEVEL);
        assertThat(received.get(0).timestamp()).isEqualTo(42);
        assertThat(received.get(0).payload()).isEqualTo("summary");
    }

    @Test
    void clear_resetsAccumulator() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 2), summariser, outputBus, OUTPUT_LEVEL);

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.clear();
        assertThat(runner.size()).isZero();
    }

    @Test
    void tick_asyncFailure_logsAndDropsByDefault() {
        Summariser<String, Integer> failingSummariser = batch ->
                                                                CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        var outputBus = new EventStreamBus<Integer>();
        var runner    = new SummarisationRunner<>(new WindowPolicy(0, 1), failingSummariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        CompletionStage<Void> result = runner.tick(5);

        assertThat(result).isNotNull();
        assertThat(result.toCompletableFuture().isCompletedExceptionally())
                .as("exception swallowed — log and drop by default").isFalse();
        assertThat(received).as("nothing published on failure").isEmpty();
    }

    // --- Edge cases ---

    @Test
    void tick_noEmit_returnsCompletedStage() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 5), summariser, outputBus, OUTPUT_LEVEL);

        CompletionStage<Void> result = runner.tick(100);

        assertThat(result).isNotNull();
        assertThat(result.toCompletableFuture().isDone()).isTrue();
        assertThat(result.toCompletableFuture().isCompletedExceptionally()).isFalse();
    }

    @Test
    void tick_summariserReturnsEmptyList_nothingPublished() {
        Summariser<String, String> summariser = Summariser.ofSync(batch -> List.of());
        var outputBus = new EventStreamBus<String>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1), summariser, outputBus, OUTPUT_LEVEL);

        List<String> received = new ArrayList<>();
        outputBus.subscribe(s -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.tick(5);

        assertThat(received).isEmpty();
        assertThat(runner.size()).as("buffer drained even with empty result").isZero();
    }

    @Test
    void tick_summariserReturnsMultipleOutputs_allPublished() {
        Summariser<String, String> summariser = Summariser.ofSync(
            batch -> List.of("first", "second", "third"));
        var outputBus = new EventStreamBus<String>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1), summariser, outputBus, OUTPUT_LEVEL);

        List<String> received = new ArrayList<>();
        outputBus.subscribe(s -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.tick(5);

        assertThat(received).containsExactly("first", "second", "third");
    }

    @Test
    void consecutiveTicks_independentWindows() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 2), summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b", 2, INPUT_LEVEL));
        runner.tick(10);
        assertThat(received).containsExactly(2);

        runner.collect(new LevelEvent<>("c", 3, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("d", 4, INPUT_LEVEL));
        runner.tick(20);
        assertThat(received).containsExactly(2, 2);
    }

    @Test
    void collect_afterTick_startsNewWindow() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 2), summariser, outputBus, OUTPUT_LEVEL);

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b", 2, INPUT_LEVEL));
        runner.tick(10);

        assertThat(runner.size()).as("drained after tick").isZero();
        runner.collect(new LevelEvent<>("c", 3, INPUT_LEVEL));
        assertThat(runner.size()).as("new event in fresh window").isEqualTo(1);
    }

    @Test
    void tick_withCompactor_appliesCompactionBeforeSummariser() {
        Compactor<String> dedup = events -> events.stream()
                                                  .distinct().toList();
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var                         outputBus  = new EventStreamBus<Integer>();
        var                         runner     = new SummarisationRunner<>(new WindowPolicy(0, 1), dedup, summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        var event = new LevelEvent<>("a", 1, INPUT_LEVEL);
        runner.collect(event);
        runner.collect(event);
        runner.tick(5);
        assertThat(received).as("compactor deduped two identical events to one").containsExactly(1);
    }

    @Test
    void tick_withCompactor_compactorReducesBatchSize() {
        Compactor<String> filterShort = events -> events.stream()
                                                        .filter(e -> e.payload().length() > 1).toList();
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var                         outputBus  = new EventStreamBus<Integer>();
        var                         runner     = new SummarisationRunner<>(new WindowPolicy(0, 1), filterShort, summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("ab", 2, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("abc", 3, INPUT_LEVEL));
        runner.tick(5);
        assertThat(received).as("compactor filtered single-char events").containsExactly(2);
    }

    @Test
    void tick_withCompactor_emptyAfterCompaction_summariserStillCalled() {
        Compactor<String>           dropAll    = events -> List.of();
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var                         outputBus  = new EventStreamBus<Integer>();
        var                         runner     = new SummarisationRunner<>(new WindowPolicy(0, 1), dropAll, summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.tick(5);
        assertThat(received).as("summariser called with empty list, publishes 0").containsExactly(0);
    }

    @Test
    void tick_failure_withHandler_callsHandlerWithBatch() {
        Summariser<String, Integer> failingSummariser = batch ->
                                                                CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        var                            outputBus = new EventStreamBus<Integer>();
        List<List<LevelEvent<String>>> recovered = new ArrayList<>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1),
                                               failingSummariser, outputBus, OUTPUT_LEVEL, recovered::add);

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.tick(5).toCompletableFuture().join();

        assertThat(recovered).as("handler received the failed batch").hasSize(1);
        assertThat(recovered.get(0)).hasSize(1);
        assertThat(recovered.get(0).get(0).payload()).isEqualTo("a");
    }

    @Test
    void tick_failure_withHandler_swallowsException() {
        Summariser<String, Integer> failingSummariser = batch ->
                                                                CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1),
                                               failingSummariser, outputBus, OUTPUT_LEVEL, batch -> {});

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        CompletionStage<Void> result = runner.tick(5);

        assertThat(result.toCompletableFuture().isCompletedExceptionally())
                .as("exception swallowed when handler set").isFalse();
    }

    @Test
    void tick_failure_withoutHandler_logsAndDrops() {
        Summariser<String, Integer> failingSummariser = batch ->
                                                                CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1),
                                               failingSummariser, outputBus, OUTPUT_LEVEL);

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        CompletionStage<Void> result = runner.tick(5);

        assertThat(result.toCompletableFuture().isCompletedExceptionally())
                .as("exception swallowed — log and drop").isFalse();
    }

    @Test
    void tick_failure_withCompactorAndHandler_handlerReceivesCompactedBatch() {
        Compactor<String> dedup = events -> events.stream().distinct().toList();
        Summariser<String, Integer> failingSummariser = batch ->
                                                                CompletableFuture.failedFuture(new RuntimeException("boom"));
        var                            outputBus = new EventStreamBus<Integer>();
        List<List<LevelEvent<String>>> recovered = new ArrayList<>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1), dedup,
                                               failingSummariser, outputBus, OUTPUT_LEVEL, recovered::add);

        var event = new LevelEvent<>("a", 1, INPUT_LEVEL);
        runner.collect(event);
        runner.collect(event);
        runner.tick(5).toCompletableFuture().join();

        assertThat(recovered).hasSize(1);
        assertThat(recovered.get(0)).as("handler receives compacted batch").hasSize(1);
    }


    @Test
    void tick_noCollect_emptyAccumulator_neverCalled() {
        var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> {
            callCount.incrementAndGet();
            return List.of(batch.size());
        });
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 1), summariser, outputBus, OUTPUT_LEVEL);

        runner.tick(100);
        runner.tick(200);
        runner.tick(300);

        assertThat(callCount.get()).as("summariser never invoked on empty buffer").isZero();
    }

    @Test
    void eventsAfterWindowFires_goIntoNextWindow() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new SummarisationRunner<>(new WindowPolicy(0, 2), summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b", 2, INPUT_LEVEL));
        runner.tick(10);
        assertThat(received).containsExactly(2);

        runner.collect(new LevelEvent<>("c", 11, INPUT_LEVEL));
        runner.tick(15);
        assertThat(received).as("one event not enough for second window").containsExactly(2);

        runner.collect(new LevelEvent<>("d", 16, INPUT_LEVEL));
        runner.tick(20);
        assertThat(received).as("second window fires").containsExactly(2, 2);
    }
}
