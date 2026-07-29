package io.casehub.blocks.summarisation;

import io.casehub.qhorus.api.spi.SummaryResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class TieredContentSummariser<T> implements ContentSummariser<T> {

    private final ContentSummariser<T> small;
    private final ContentSummariser<T> medium;
    private final ContentSummariser<T> large;
    private final int smallThreshold;
    private final int mediumThreshold;

    public TieredContentSummariser(
            ContentSummariser<T> small,
            ContentSummariser<T> large,
            int smallThreshold) {
        this(small, large, large, smallThreshold, smallThreshold);
    }

    public TieredContentSummariser(
            ContentSummariser<T> small,
            ContentSummariser<T> medium,
            ContentSummariser<T> large,
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
