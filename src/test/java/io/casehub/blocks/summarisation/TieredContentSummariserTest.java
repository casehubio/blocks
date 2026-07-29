package io.casehub.blocks.summarisation;

import io.casehub.qhorus.api.spi.SummaryResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TieredContentSummariserTest {

    private final AtomicReference<String> lastTier = new AtomicReference<>();

    private ContentSummariser<String> tierRecorder(String tierName) {
        return (items, prev) -> {
            lastTier.set(tierName);
            return CompletableFuture.completedFuture(
                    SummaryResult.ofText(tierName + ":" + items.size()));
        };
    }

    @Test
    void threeTier_dispatchesBySize() {
        var tiered = new TieredContentSummariser<>(
                tierRecorder("small"), tierRecorder("medium"), tierRecorder("large"),
                3, 10);

        tiered.summarise(items(2), null).toCompletableFuture().join();
        assertThat(lastTier.get()).isEqualTo("small");

        tiered.summarise(items(3), null).toCompletableFuture().join();
        assertThat(lastTier.get()).isEqualTo("small");

        tiered.summarise(items(4), null).toCompletableFuture().join();
        assertThat(lastTier.get()).isEqualTo("medium");

        tiered.summarise(items(10), null).toCompletableFuture().join();
        assertThat(lastTier.get()).isEqualTo("medium");

        tiered.summarise(items(11), null).toCompletableFuture().join();
        assertThat(lastTier.get()).isEqualTo("large");
    }

    @Test
    void twoTier_smallAndLargeOnly() {
        var tiered = new TieredContentSummariser<>(
                tierRecorder("small"), tierRecorder("large"), 5);

        tiered.summarise(items(5), null).toCompletableFuture().join();
        assertThat(lastTier.get()).isEqualTo("small");

        tiered.summarise(items(6), null).toCompletableFuture().join();
        assertThat(lastTier.get()).isEqualTo("large");
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThatThrownBy(() -> new TieredContentSummariser<>(
                tierRecorder("s"), tierRecorder("l"), 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new TieredContentSummariser<>(
                tierRecorder("s"), tierRecorder("m"), tierRecorder("l"), 5, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passesPreviousToDelegates() {
        var captured = new AtomicReference<SummaryResult>();
        ContentSummariser<String> capturingDelegate = (items, prev) -> {
            captured.set(prev);
            return CompletableFuture.completedFuture(SummaryResult.ofText("ok"));
        };
        var tiered = new TieredContentSummariser<>(capturingDelegate, capturingDelegate, 5);
        var prev = new SummaryResult("prior", Map.of("k", "v"));

        tiered.summarise(items(2), prev).toCompletableFuture().join();
        assertThat(captured.get()).isSameAs(prev);
    }

    private List<String> items(int n) {
        var list = new ArrayList<String>();
        for (int i = 0; i < n; i++) list.add("item-" + i);
        return list;
    }
}
