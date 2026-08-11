package io.casehub.blocks.summarisation;

import io.casehub.qhorus.api.spi.SummaryResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSummariserToSummariserTest {

    @Test
    void unwrapsLevelEventsAndDelegates() {
        ContentSummariser<String> delegate = (items, prev) ->
                CompletableFuture.completedFuture(
                        SummaryResult.ofText("summarised:" + items.size()));

        Summariser<String, String> adapted = new ContentSummariserToSummariser<>(delegate);

        var events = List.of(
                new LevelEvent<>("a", 1000L, new EventLevel("raw", 0)),
                new LevelEvent<>("b", 2000L, new EventLevel("raw", 0)));

        var result = adapted.summarise(events).toCompletableFuture().join();

        assertThat(result).containsExactly("summarised:2");
    }

    @Test
    void passesNullPrevious() {
        var capturedPrev = new AtomicReference<SummaryResult>();
        ContentSummariser<String> delegate = (items, prev) -> {
            capturedPrev.set(prev);
            return CompletableFuture.completedFuture(SummaryResult.ofText("ok"));
        };
        var adapted = new ContentSummariserToSummariser<>(delegate);
        adapted.summarise(List.of(new LevelEvent<>("x", 1L, new EventLevel("raw", 0))))
                .toCompletableFuture().join();
        assertThat(capturedPrev.get()).isNull();
    }
}
