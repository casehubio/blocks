package io.casehub.blocks.summarisation;

import io.casehub.qhorus.api.spi.SummaryResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class TieredContentSummariser<T> implements ContentSummariser<T, SummaryResult> {

    private final ContentSummariser<T, SummaryResult> small;
    private final ContentSummariser<T, SummaryResult> medium;
    private final ContentSummariser<T, SummaryResult> large;
    private final int smallThreshold;
    private final int mediumThreshold;

    public TieredContentSummariser(
            ContentSummariser<T, SummaryResult> small,
            ContentSummariser<T, SummaryResult> large,
            int smallThreshold) {
        this(small, large, large, smallThreshold, smallThreshold);
    }

    public TieredContentSummariser(
            ContentSummariser<T, SummaryResult> small,
            ContentSummariser<T, SummaryResult> medium,
            ContentSummariser<T, SummaryResult> large,
            int smallThreshold,
            int mediumThreshold) {
        if (smallThreshold < 1)
            throw new IllegalArgumentException("smallThreshold must be >= 1, was: " + smallThreshold);
        if (mediumThreshold < smallThreshold)
            throw new IllegalArgumentException("mediumThreshold must be >= smallThreshold, was: "
                    + mediumThreshold + " < " + smallThreshold);
        this.small = small;
        this.medium = medium;
        this.large = large;
        this.smallThreshold = smallThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<T> items, @Nullable SummaryResult previous) {
        if (items.size() <= smallThreshold) return small.summarise(items, previous);
        if (items.size() <= mediumThreshold) return medium.summarise(items, previous);
        return large.summarise(items, previous);
    }
}
