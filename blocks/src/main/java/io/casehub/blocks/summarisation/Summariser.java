package io.casehub.blocks.summarisation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface Summariser<IN, OUT> {
    CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>> batch);

    static <IN, OUT> Summariser<IN, OUT> ofSync(SyncSummariser<IN, OUT> sync) {
        return batch -> CompletableFuture.completedFuture(sync.summarise(batch));
    }

    @FunctionalInterface
    interface SyncSummariser<IN, OUT> {
        List<OUT> summarise(List<LevelEvent<IN>> batch);
    }
}
