package io.casehub.blocks.summarisation;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class ContentSummariserToSummariser<T> implements Summariser<T, String> {

    private final ContentSummariser<T> delegate;

    public ContentSummariserToSummariser(ContentSummariser<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletionStage<List<String>> summarise(List<LevelEvent<T>> batch) {
        List<T> items = batch.stream().map(LevelEvent::payload).toList();
        return delegate.summarise(items, null)
                .thenApply(result -> List.of(result.text()));
    }
}
