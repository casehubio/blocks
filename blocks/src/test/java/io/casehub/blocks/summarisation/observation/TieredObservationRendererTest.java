package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

class TieredObservationRendererTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    Function<String, String> eventRenderer = s -> s;
    Function<String, String> groupKeyExtractor = s -> s.split(":")[0];
    Summariser<String, String> summariser = batch ->
            CompletableFuture.completedFuture(List.of("Summary of " + batch.size() + " events"));

    private LevelEvent<String> event(String payload, long timestamp) {
        return new LevelEvent<>(payload, timestamp, LEVEL);
    }

    private ObservationContext ctx(long now, long sinceLast) {
        return new ObservationContext(now, sinceLast);
    }

    // --- Constructor validation ---

    @Test
    void rejects_negativeVerbatimThreshold() {
        assertThatThrownBy(() -> new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verbatimThreshold");
    }

    @Test
    void rejects_groupedThresholdNotGreaterThanVerbatim() {
        assertThatThrownBy(() -> new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 5, 5, summariser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupedThreshold");
    }

    @Test
    void rejects_groupedThresholdLessThanVerbatim() {
        assertThatThrownBy(() -> new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 5, 3, summariser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupedThreshold");
    }

    @Test
    void accepts_zeroVerbatimThreshold() {
        assertThatCode(() -> new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 0))
                .doesNotThrowAnyException();
    }

    // --- Verbatim tier ---

    @Test
    void verbatim_belowThreshold_rendersEachEvent() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5);
        var events = List.of(event("hello", 1000), event("world", 2000));
        var result = renderer.render(events, ctx(3000, 5000))
                .toCompletableFuture().join();

        assertThat(result.tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(result.eventCount()).isEqualTo(2);
        assertThat(result.renderedText()).contains("[2s ago]", "hello");
        assertThat(result.renderedText()).contains("[1s ago]", "world");
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks().get(0).content()).isEqualTo("hello");
        assertThat(result.chunks().get(0).eventCount()).isEqualTo(1);
        assertThat(result.chunks().get(0).tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(result.chunks().get(0).timestamp()).isEqualTo(1000);
    }

    @Test
    void verbatim_exactlyAtThreshold_usesVerbatim() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 3);
        var events = List.of(event("a", 1), event("b", 2), event("c", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();
        assertThat(result.tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(result.chunks()).hasSize(3);
    }

    // --- Grouped tier ---

    @Test
    void grouped_aboveVerbatimThreshold_groupsByKey() {
        var renderer = new TieredObservationRenderer<String>(
                s -> s.split(":")[1], s -> s.split(":")[0], 2);
        var events = List.of(
                event("move:entered room", 1),
                event("talk:said hello", 2),
                event("move:left room", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();

        assertThat(result.tier()).isEqualTo(ObservationTier.GROUPED);
        assertThat(result.eventCount()).isEqualTo(3);
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks().get(0).metadata()).containsEntry("groupKey", "move");
        assertThat(result.chunks().get(0).eventCount()).isEqualTo(2);
        assertThat(result.chunks().get(1).metadata()).containsEntry("groupKey", "talk");
        assertThat(result.chunks().get(1).eventCount()).isEqualTo(1);
        assertThat(result.renderedText()).contains("move: entered room. left room");
        assertThat(result.renderedText()).contains("talk: said hello");
    }

    // --- Summarised tier ---

    @Test
    void summarised_aboveGroupedThreshold_delegatesToSummariser() {
        var renderer = new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 2, 4, summariser);
        var events = List.of(
                event("a", 1), event("b", 2), event("c", 3),
                event("d", 4), event("e", 5));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();

        assertThat(result.tier()).isEqualTo(ObservationTier.SUMMARISED);
        assertThat(result.eventCount()).isEqualTo(5);
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).content()).isEqualTo("Summary of 5 events");
        assertThat(result.chunks().get(0).eventCount()).isEqualTo(5);
        assertThat(result.chunks().get(0).tier()).isEqualTo(ObservationTier.SUMMARISED);
    }

    @Test
    void summarised_multiElementList_joinedWithNewlines() {
        Summariser<String, String> multiPartSummariser = batch ->
                CompletableFuture.completedFuture(List.of("Part one.", "Part two."));
        var renderer = new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 1, 2, multiPartSummariser);
        var events = List.of(event("a", 1), event("b", 2), event("c", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();

        assertThat(result.chunks().get(0).content()).isEqualTo("Part one.\nPart two.");
    }

    // --- Two-tier (no summariser) ---

    @Test
    void twoTier_aboveVerbatim_usesGrouped() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 2);
        var events = List.of(event("a:1", 1), event("b:2", 2), event("a:3", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();
        assertThat(result.tier()).isEqualTo(ObservationTier.GROUPED);
    }

    @Test
    void twoTier_largeBuffer_stillUsesGrouped() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 2);
        var events = new java.util.ArrayList<LevelEvent<String>>();
        for (int i = 0; i < 100; i++) {
            events.add(event("g" + (i % 3) + ":event" + i, i));
        }
        var result = renderer.render(events, ctx(200, 5000))
                .toCompletableFuture().join();
        assertThat(result.tier()).isEqualTo(ObservationTier.GROUPED);
    }

    // --- Empty events ---

    @Test
    void emptyEvents_returnsEmpty() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5);
        var result = renderer.render(List.of(), ctx(10, 5000))
                .toCompletableFuture().join();
        assertThat(result.eventCount()).isZero();
        assertThat(result.tier()).isNull();
        assertThat(result.renderedText()).isEmpty();
    }

    // --- Header ---

    @Test
    void defaultHeader_includesElapsedTime() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5);
        var result = renderer.render(List.of(event("x", 1)), ctx(10, 8000))
                .toCompletableFuture().join();
        assertThat(result.renderedText()).contains("8 seconds since your last action");
    }

    @Test
    void customHeader_replacesDefault() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5)
                .withHeaderFormatter(ctx -> "== Custom (" + ctx.timeSinceLastDrain() + "ms) ==");
        var result = renderer.render(List.of(event("x", 1)), ctx(10, 3000))
                .toCompletableFuture().join();
        assertThat(result.renderedText()).contains("== Custom (3000ms) ==");
        assertThat(result.renderedText()).doesNotContain("What Just Happened");
    }

    // --- Format helpers ---

    @Test
    void formatDuration_variousRanges() {
        assertThat(TieredObservationRenderer.formatDuration(500)).isEqualTo("500ms");
        assertThat(TieredObservationRenderer.formatDuration(1000)).isEqualTo("1 second");
        assertThat(TieredObservationRenderer.formatDuration(8000)).isEqualTo("8 seconds");
        assertThat(TieredObservationRenderer.formatDuration(60000)).isEqualTo("1 minute");
        assertThat(TieredObservationRenderer.formatDuration(120000)).isEqualTo("2 minutes");
    }

    @Test
    void formatAgo_variousRanges() {
        assertThat(TieredObservationRenderer.formatAgo(500)).isEqualTo("500ms");
        assertThat(TieredObservationRenderer.formatAgo(2000)).isEqualTo("2s");
        assertThat(TieredObservationRenderer.formatAgo(65000)).isEqualTo("1m");
    }
}
