package io.casehub.blocks.summarisation;

import io.casehub.qhorus.api.spi.SummaryResult;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class VerbatimContentSummariser<T> implements ContentSummariser<T> {

    private final Function<T, String> renderer;

    public VerbatimContentSummariser(Function<T, String> renderer) {
        this.renderer = renderer;
    }

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<T> items, @Nullable SummaryResult previous) {
        var sb = new StringBuilder();
        if (previous != null && !previous.text().isBlank()) {
            sb.append(previous.text()).append("\n\n");
        }
        for (var item : items) {
            sb.append("- ").append(renderer.apply(item)).append('\n');
        }
        var annotations = new HashMap<>(
                previous != null ? previous.annotations() : Map.of());
        annotations.put("tier", "verbatim");
        annotations.put("itemCount", String.valueOf(items.size()));
        return CompletableFuture.completedFuture(
                new SummaryResult(sb.toString().stripTrailing(), annotations));
    }
}
