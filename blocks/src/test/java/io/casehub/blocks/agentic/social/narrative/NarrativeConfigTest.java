package io.casehub.blocks.agentic.social.narrative;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeConfigTest {

    @Test
    void synthesisGate_defaults() {
        var gate = NarrativeSynthesisGate.defaults();
        assertThat(gate.minNewReflections()).isEqualTo(5);
        assertThat(gate.noveltyThreshold()).isEqualTo(0.3);
        assertThat(gate.quietPeriodBypass()).isEqualTo(Duration.ofMinutes(120));
    }

    @Test
    void synthesisGate_rejectsZeroReflections() {
        assertThatThrownBy(() -> new NarrativeSynthesisGate(0, 0.3, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void synthesisGate_rejectsNoveltyAboveOne() {
        assertThatThrownBy(() -> new NarrativeSynthesisGate(5, 1.5, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void synthesisGate_rejectsNegativeNovelty() {
        assertThatThrownBy(() -> new NarrativeSynthesisGate(5, -0.1, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void synthesisGate_rejectsNullDuration() {
        assertThatThrownBy(() -> new NarrativeSynthesisGate(5, 0.3, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void config_defaults() {
        var config = NarrativeConfig.defaults();
        assertThat(config.maxEpisodes()).isEqualTo(50);
        assertThat(config.maxThemes()).isEqualTo(10);
        assertThat(config.themeSalienceFloor()).isEqualTo(0.1);
        assertThat(config.maxReflectionsPerSynthesis()).isEqualTo(20);
        assertThat(config.synthesisGate()).isNotNull();
        assertThat(config.memoryDomain()).isEqualTo("narrative");
        assertThat(config.caseType()).isEqualTo("narrative");}

    @Test
    void config_rejectsInvalidMaxEpisodes() {
        assertThatThrownBy(() -> new NarrativeConfig(
                NarrativeSynthesisGate.defaults(), 0, 10, 0.1, 20, "narrative", "narrative"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_rejectsInvalidMaxThemes() {
        assertThatThrownBy(() -> new NarrativeConfig(
                NarrativeSynthesisGate.defaults(), 50, 0, 0.1, 20, "narrative", "narrative"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_rejectsInvalidSalienceFloor() {
        assertThatThrownBy(() -> new NarrativeConfig(
                NarrativeSynthesisGate.defaults(), 50, 10, 1.5, 20, "narrative", "narrative"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void config_rejectsInvalidMaxReflections() {
        assertThatThrownBy(() -> new NarrativeConfig(
                NarrativeSynthesisGate.defaults(), 50, 10, 0.1, 0, "narrative", "narrative"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noOpNarrativeStore_loadReturnsNull() {
        var store = new NoOpNarrativeStore();
        assertThat(store.load("agent-1", "tenant-1")).isNull();
    }

    @Test
    void noOpNarrativeStore_storeNoOp() {
        var store = new NoOpNarrativeStore();
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(), Instant.now(), 0);
        assertThatCode(() -> store.store(state)).doesNotThrowAnyException();
    }

    @Test
    void noOpReflectionQueryStore_findSinceReturnsEmpty() {
        var store = new io.casehub.blocks.memory.NoOpReflectionQueryStore();
        assertThat(store.findSince("a", "t", Instant.EPOCH)).isEmpty();
    }

    @Test
    void noOpReflectionQueryStore_countSinceReturnsZero() {
        var store = new io.casehub.blocks.memory.NoOpReflectionQueryStore();
        assertThat(store.countSince("a", "t", Instant.EPOCH)).isEqualTo(0);
    }

    @Test
    void narrativeTick_noChange() {
        var tick = new NarrativeTick.NoChange("no new reflections");
        assertThat(tick.reason()).isEqualTo("no new reflections");
    }

    @Test
    void narrativeTick_updated() {
        var now = Instant.now();
        var prev = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL, List.of(), now, 0);
        var curr = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL, List.of(), now, 5);
        var tick = new NarrativeTick.Updated(prev, curr, List.of("e1"), List.of("helper"));
        assertThat(tick.newEpisodeIds()).containsExactly("e1");
        assertThat(tick.newThemeLabels()).containsExactly("helper");
    }

    @Test
    void narrativeSynthesisTick_skipped() {
        var tick = new NarrativeSynthesisTick.Skipped("below threshold");
        assertThat(tick.reason()).isEqualTo("below threshold");
    }

    @Test
    void narrativeSynthesisTick_synthesised() {
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(), Instant.now(), 5);
        var tick = new NarrativeSynthesisTick.Synthesised(state, 5);
        assertThat(tick.newReflectionsConsumed()).isEqualTo(5);
        assertThat(tick.state()).isEqualTo(state);
    }
}
