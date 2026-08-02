package io.casehub.blocks.summarisation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class SummarisationRunner<IN, OUT> {

    private static final System.Logger LOG = System.getLogger(SummarisationRunner.class.getName());

    private final EventAccumulator<IN>           accumulator;
    private final Compactor<IN>                  compactor;
    private final Summariser<IN, OUT>            summariser;
    private final EventStreamBus<OUT>            outputBus;
    private final EventLevel                     outputLevel;
    private final Consumer<List<LevelEvent<IN>>> onFailure;

    public SummarisationRunner(WindowPolicy policy,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel) {
        this(policy, null, summariser, outputBus, outputLevel, null);
    }

    public SummarisationRunner(WindowPolicy policy,
                               Compactor<IN> compactor,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel) {
        this(policy, compactor, summariser, outputBus, outputLevel, null);
    }

    public SummarisationRunner(WindowPolicy policy,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel,
                               Consumer<List<LevelEvent<IN>>> onFailure) {
        this(policy, null, summariser, outputBus, outputLevel, onFailure);
    }

    public SummarisationRunner(WindowPolicy policy,
                               Compactor<IN> compactor,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel,
                               Consumer<List<LevelEvent<IN>>> onFailure) {
        this.accumulator = new EventAccumulator<>(policy);
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
     * Drains ready events, applies compaction, and submits to the summariser.
     * Synchronized — concurrent tick() calls are serialized. The hot path
     * (no events ready) acquires and releases the lock without blocking.
     */
    public synchronized CompletionStage<Void> tick(long now) {
        var batch = accumulator.drainIfReady(now);
        if (batch.isEmpty()) {return CompletableFuture.completedFuture(null);}
        if (compactor != null) {
            batch = compactor.compact(batch);
        }
        var finalBatch = batch;
        return summariser.summarise(batch).thenAccept(results -> {
            for (var payload : results) {
                outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
            }
        }).handle((v, ex) -> {
            if (ex != null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Summarisation failed, batch size=" + finalBatch.size(), ex);
                if (onFailure != null) {
                    onFailure.accept(finalBatch);
                }
            }
            return null;
        });
    }


    /**
     * Unconditional drain — bypasses WindowPolicy. Use at shutdown to
     * capture all remaining buffered events regardless of count or age.
     */
    public synchronized CompletionStage<Void> flush() {
        var batch = accumulator.drain();
        if (batch.isEmpty()) {return CompletableFuture.completedFuture(null);}
        if (compactor != null) {
            batch = compactor.compact(batch);
        }
        var  finalBatch = batch;
        long now        = System.currentTimeMillis();
        return summariser.summarise(batch).thenAccept(results -> {
            for (var payload : results) {
                outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
            }
        }).handle((v, ex) -> {
            if (ex != null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Flush failed, batch size=" + finalBatch.size(), ex);
                if (onFailure != null) {
                    onFailure.accept(finalBatch);
                }
            }
            return null;
        });
    }

    public void clear() {
        accumulator.clear();
    }

    public int size() {
        return accumulator.size();
    }
}
