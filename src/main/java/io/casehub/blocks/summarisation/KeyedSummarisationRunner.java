package io.casehub.blocks.summarisation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class KeyedSummarisationRunner<K, IN, OUT> {

    private static final System.Logger LOG = System.getLogger(KeyedSummarisationRunner.class.getName());

    private final KeyedAccumulator<K, IN>        accumulator;
    private final Compactor<IN>                  compactor;
    private final Summariser<IN, OUT>            summariser;
    private final EventStreamBus<OUT>            outputBus;
    private final EventLevel                     outputLevel;
    private final Consumer<List<LevelEvent<IN>>> onFailure;

    public KeyedSummarisationRunner(Function<IN, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel) {
        this(keyExtractor, completionTest, staleTimeout, null, summariser, outputBus, outputLevel, null);
    }

    public KeyedSummarisationRunner(Function<IN, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Compactor<IN> compactor,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel) {
        this(keyExtractor, completionTest, staleTimeout, compactor, summariser, outputBus, outputLevel, null);
    }

    public KeyedSummarisationRunner(Function<IN, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel,
                                    Consumer<List<LevelEvent<IN>>> onFailure) {
        this(keyExtractor, completionTest, staleTimeout, null, summariser, outputBus, outputLevel, onFailure);
    }

    public KeyedSummarisationRunner(Function<IN, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Compactor<IN> compactor,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel,
                                    Consumer<List<LevelEvent<IN>>> onFailure) {
        this.accumulator = new KeyedAccumulator<>(keyExtractor, completionTest, staleTimeout);
        this.compactor   = compactor;
        this.summariser  = summariser;
        this.outputBus   = outputBus;
        this.outputLevel = outputLevel;
        this.onFailure   = onFailure;
    }

    public void collect(LevelEvent<IN> event) {
        accumulator.collect(event);
    }

    /**
     * Drains completed/stale groups, applies compaction, and submits each to the summariser.
     * Synchronized — concurrent tick() calls are serialized. The hot path
     * (no groups ready) acquires and releases the lock without blocking.
     */
    public synchronized CompletionStage<Void> tick(long now) {
        var groups = accumulator.drain(now);
        if (groups.isEmpty()) {return CompletableFuture.completedFuture(null);}
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = groups.stream()
                                                  .map(group -> {
                                                      var batch = compactor != null ? compactor.compact(group) : group;
                                                      return summariser.summarise(batch).thenAccept(results -> {
                                                          for (var payload : results) {
                                                              outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
                                                          }
                                                      }).handle((v, ex) -> {
                                                          if (ex != null) {
                                                              LOG.log(System.Logger.Level.WARNING,
                                                                      "Summarisation failed, batch size=" + batch.size(), ex);
                                                              if (onFailure != null) {
                                                                  onFailure.accept(batch);
                                                              }
                                                          }
                                                          return (Void) null;
                                                      }).toCompletableFuture();
                                                  })
                                                  .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }


    /**
     * Unconditional drain — bypasses completion test and stale timeout.
     * Use at shutdown to capture all remaining buffered events.
     */
    public synchronized CompletionStage<Void> flush() {
        var groups = accumulator.drainAll();
        if (groups.isEmpty()) {return CompletableFuture.completedFuture(null);}
        long now = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = groups.stream()
                                                  .map(group -> {
                                                      var batch = compactor != null ? compactor.compact(group) : group;
                                                      return summariser.summarise(batch).thenAccept(results -> {
                                                          for (var payload : results) {
                                                              outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
                                                          }
                                                      }).handle((v, ex) -> {
                                                          if (ex != null) {
                                                              LOG.log(System.Logger.Level.WARNING,
                                                                      "Flush failed, batch size=" + batch.size(), ex);
                                                              if (onFailure != null) {
                                                                  onFailure.accept(batch);
                                                              }
                                                          }
                                                          return (Void) null;
                                                      }).toCompletableFuture();
                                                  })
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
