package io.casehub.blocks.summarisation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

public class KeyedSummarisationRunner<K, IN, OUT> {

    private final KeyedAccumulator<K, IN> accumulator;
    private final Summariser<IN, OUT> summariser;
    private final EventStreamBus<OUT> outputBus;
    private final EventLevel outputLevel;

    public KeyedSummarisationRunner(Function<IN, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel) {
        this.accumulator = new KeyedAccumulator<>(keyExtractor, completionTest, staleTimeout);
        this.summariser = summariser;
        this.outputBus = outputBus;
        this.outputLevel = outputLevel;
    }

    public void collect(LevelEvent<IN> event) {
        accumulator.collect(event);
    }

    public CompletionStage<Void> tick(long now) {
        var groups = accumulator.drain(now);
        if (groups.isEmpty())
            return CompletableFuture.completedFuture(null);
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = groups.stream()
            .map(group -> summariser.summarise(group).thenAccept(results -> {
                for (var payload : results) {
                    outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
                }
            }).toCompletableFuture())
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public void clear() {
        accumulator.clear();
    }

    public int groupCount() {
        return accumulator.groupCount();
    }

    public int eventCount() {
        return accumulator.eventCount();
    }
}
