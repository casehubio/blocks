package io.casehub.blocks.speech;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record CleanupConfig(List<TextFilter> filters, int maxDestructiveness) {

    public CleanupConfig {
        Objects.requireNonNull(filters, "filters");
        filters = filters.stream()
                .filter(f -> f.destructiveness() <= maxDestructiveness)
                .sorted(Comparator.comparingInt(TextFilter::destructiveness))
                .toList();
    }

    public String apply(String text) {
        for (TextFilter filter : filters) {
            text = filter.apply(text);
        }
        return text;
    }

    public static CleanupConfig of(TextFilter... filters) {
        return new CleanupConfig(List.of(filters), Integer.MAX_VALUE);
    }

    public static CleanupConfig upTo(int maxDestructiveness, TextFilter... filters) {
        return new CleanupConfig(List.of(filters), maxDestructiveness);
    }

    public CleanupConfig withMaxDestructiveness(int max) {
        return new CleanupConfig(new ArrayList<>(filters), max);
    }
}
