package io.casehub.blocks.summarisation.yaml;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TieredSummariserRegistrationTest {

    private SummariserRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SummariserRegistry();
        registry.register("upper", (SummariserFactory) config ->
                Summariser.ofSync(batch -> batch.stream()
                        .map(e -> ((LevelEvent<?>) e).payload().toString().toUpperCase())
                        .toList()));
        registry.register("lower", (SummariserFactory) config ->
                Summariser.ofSync(batch -> batch.stream()
                        .map(e -> ((LevelEvent<?>) e).payload().toString().toLowerCase())
                        .toList()));
    }

    @Test
    void dispatches_to_small_delegate_at_or_below_threshold() {
        Summariser<String, Object> tiered = registry.create("tiered", Map.of(
                "smallThreshold", 2,
                "small", "upper",
                "large", "lower"));

        var batch = List.of(
                new LevelEvent<>("hello", 1L, new EventLevel("L1", 1), null));

        var result = tiered.summarise(batch).toCompletableFuture().join();
        assertThat(result).containsExactly("HELLO");
    }

    @Test
    void dispatches_to_large_delegate_above_threshold() {
        Summariser<String, Object> tiered = registry.create("tiered", Map.of(
                "smallThreshold", 1,
                "small", "upper",
                "large", "lower"));

        var batch = List.of(
                new LevelEvent<>("Hello", 1L, new EventLevel("L1", 1), null),
                new LevelEvent<>("World", 2L, new EventLevel("L1", 1), null));

        var result = tiered.summarise(batch).toCompletableFuture().join();
        assertThat(result).containsExactly("hello", "world");
    }

    @Test
    void supports_three_tier_with_medium_delegate() {
        Summariser<String, Object> tiered = registry.create("tiered", Map.of(
                "smallThreshold", 1,
                "mediumThreshold", 3,
                "small", "upper",
                "medium", "lower",
                "large", "upper"));

        var batch = List.of(
                new LevelEvent<>("A", 1L, new EventLevel("L1", 1), null),
                new LevelEvent<>("B", 2L, new EventLevel("L1", 1), null));

        var result = tiered.summarise(batch).toCompletableFuture().join();
        assertThat(result).containsExactly("a", "b");
    }

    @Test
    void defaults_medium_to_large_when_not_specified() {
        Summariser<String, Object> tiered = registry.create("tiered", Map.of(
                "smallThreshold", 1,
                "mediumThreshold", 3,
                "small", "upper",
                "large", "lower"));

        var batch = List.of(
                new LevelEvent<>("A", 1L, new EventLevel("L1", 1), null),
                new LevelEvent<>("B", 2L, new EventLevel("L1", 1), null));

        var result = tiered.summarise(batch).toCompletableFuture().join();
        assertThat(result).containsExactly("a", "b");
    }

    @Test
    void rejects_missing_small_threshold() {
        assertThatThrownBy(() -> registry.create("tiered", Map.of(
                "small", "upper",
                "large", "lower")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smallThreshold");
    }

    @Test
    void rejects_missing_large_delegate() {
        assertThatThrownBy(() -> registry.create("tiered", Map.of(
                "smallThreshold", 2,
                "small", "upper")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("large");
    }

    @Test
    void rejects_unknown_delegate_type() {
        assertThatThrownBy(() -> registry.create("tiered", Map.of(
                "smallThreshold", 2,
                "small", "nonexistent",
                "large", "lower")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }
}
