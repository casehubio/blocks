package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TieredObservationRenderer<E> implements ObservationRenderer<E> {

    private final Function<E, String> eventRenderer;
    private final Function<E, String> groupKeyExtractor;
    private final int verbatimThreshold;
    private final int groupedThreshold;
    private final Summariser<E, String> summariser;
    private final Function<ObservationContext, String> headerFormatter;

    public TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold) {
        this(eventRenderer, groupKeyExtractor, verbatimThreshold,
                Integer.MAX_VALUE, null, null);
    }

    public TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold,
            int groupedThreshold,
            Summariser<E, String> summariser) {
        this(eventRenderer, groupKeyExtractor, verbatimThreshold,
                groupedThreshold, summariser, null);
    }

    private TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold,
            int groupedThreshold,
            Summariser<E, String> summariser,
            Function<ObservationContext, String> headerFormatter) {
        if (verbatimThreshold < 0) {
            throw new IllegalArgumentException(
                    "verbatimThreshold must be >= 0, was: " + verbatimThreshold);
        }
        if (summariser != null && groupedThreshold <= verbatimThreshold) {
            throw new IllegalArgumentException(
                    "groupedThreshold must be > verbatimThreshold, was: "
                    + groupedThreshold + " <= " + verbatimThreshold);
        }
        this.eventRenderer = eventRenderer;
        this.groupKeyExtractor = groupKeyExtractor;
        this.verbatimThreshold = verbatimThreshold;
        this.groupedThreshold = summariser != null ? groupedThreshold : Integer.MAX_VALUE;
        this.summariser = summariser;
        this.headerFormatter = headerFormatter != null
                ? headerFormatter : TieredObservationRenderer::defaultHeader;
    }

    public TieredObservationRenderer<E> withHeaderFormatter(
            Function<ObservationContext, String> headerFormatter) {
        return new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, verbatimThreshold,
                summariser != null ? groupedThreshold : Integer.MAX_VALUE,
                summariser, headerFormatter);
    }

    @Override
    public CompletionStage<ObservationResult> render(
            List<LevelEvent<E>> events, ObservationContext context) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ObservationResult.empty(context.timeSinceLastDrain()));
        }
        String header = headerFormatter.apply(context);
        if (events.size() <= verbatimThreshold) {
            return CompletableFuture.completedFuture(
                    renderVerbatim(events, context, header));
        } else if (events.size() <= groupedThreshold) {
            return CompletableFuture.completedFuture(
                    renderGrouped(events, context, header));
        } else {
            return renderSummarised(events, context, header);
        }
    }

    private ObservationResult renderVerbatim(
            List<LevelEvent<E>> events, ObservationContext context, String header) {
        var sb = new StringBuilder(header).append("\n");
        var chunks = new ArrayList<ObservationChunk>();
        for (var event : events) {
            long ago = context.currentTime() - event.timestamp();
            String text = eventRenderer.apply(event.payload());
            sb.append("- [").append(formatAgo(ago)).append(" ago] ")
              .append(text).append("\n");
            chunks.add(new ObservationChunk(
                    text, event.timestamp(), ObservationTier.VERBATIM, 1, Map.of()));
        }
        return new ObservationResult(sb.toString(), chunks, events.size(),
                context.timeSinceLastDrain(), ObservationTier.VERBATIM);
    }

    private ObservationResult renderGrouped(
            List<LevelEvent<E>> events, ObservationContext context, String header) {
        var groups = new LinkedHashMap<String, List<LevelEvent<E>>>();
        for (var event : events) {
            groups.computeIfAbsent(groupKeyExtractor.apply(event.payload()),
                    k -> new ArrayList<>()).add(event);
        }
        var sb = new StringBuilder(header).append("\n");
        var chunks = new ArrayList<ObservationChunk>();
        for (var entry : groups.entrySet()) {
            String groupText = entry.getValue().stream()
                    .map(e -> eventRenderer.apply(e.payload()))
                    .collect(Collectors.joining(". "));
            String line = entry.getKey() + ": " + groupText;
            sb.append(line).append("\n");
            chunks.add(new ObservationChunk(
                    line, context.currentTime(), ObservationTier.GROUPED,
                    entry.getValue().size(),
                    Map.of("groupKey", entry.getKey())));
        }
        return new ObservationResult(sb.toString(), chunks, events.size(),
                context.timeSinceLastDrain(), ObservationTier.GROUPED);
    }

    private CompletionStage<ObservationResult> renderSummarised(
            List<LevelEvent<E>> events, ObservationContext context, String header) {
        return summariser.summarise(events).thenApply(parts -> {
            String summary = String.join("\n", parts);
            var sb = new StringBuilder(header).append("\n")
                    .append(summary).append("\n");
            var chunk = new ObservationChunk(
                    summary, context.currentTime(), ObservationTier.SUMMARISED,
                    events.size(), Map.of());
            return new ObservationResult(sb.toString(), List.of(chunk),
                    events.size(), context.timeSinceLastDrain(),
                    ObservationTier.SUMMARISED);
        });
    }

    private static String defaultHeader(ObservationContext context) {
        return "== What Just Happened ("
                + formatDuration(context.timeSinceLastDrain())
                + " since your last action) ==";
    }

    static String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + " second" + (seconds == 1 ? "" : "s");
        long minutes = seconds / 60;
        return minutes + " minute" + (minutes == 1 ? "" : "s");
    }

    static String formatAgo(long millis) {
        if (millis < 1000) return millis + "ms";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        return minutes + "m";
    }
}
