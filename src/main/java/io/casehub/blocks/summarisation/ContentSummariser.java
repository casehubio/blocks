package io.casehub.blocks.summarisation;

import io.casehub.qhorus.api.spi.SummaryResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ContentSummariser<T> {
    CompletionStage<SummaryResult> summarise(
            List<T> items, @Nullable SummaryResult previous);
}
