package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportanceScorerTest {

    private static ScoredCbrCase<CbrCase> scored(String problem, String solution,
                                                  Map<String, FeatureValue> features) {
        var c = new FeatureVectorCbrCase(problem, solution, null, null, features, null, null);
        return new ScoredCbrCase<>(c, "case-1", 0.5);
    }

    @Test
    void arousalScorerReturnsLowForNeutralText() {
        var scorer = new ArousalScorer();
        var memory = scored("routine update completed", "nothing notable happened", Map.of());
        assertThat(scorer.score(memory, Instant.now())).isBetween(0.0, 0.3);
    }

    @Test
    void arousalScorerReturnsHighForEmotionalText() {
        var scorer = new ArousalScorer();
        var memory = scored("critical emergency failure detected", "urgent crisis escalation required",
                Map.of());
        assertThat(scorer.score(memory, Instant.now())).isGreaterThan(0.3);
    }

    @Test
    void arousalScorerHandlesNullSolution() {
        var c = new FeatureVectorCbrCase("test problem", "n/a", null, null, Map.of(), null, null);
        var memory = new ScoredCbrCase<CbrCase>(c, "case-1", 0.5);
        var scorer = new ArousalScorer();
        assertThat(scorer.score(memory, Instant.now())).isBetween(0.0, 1.0);
    }

    @Test
    void surpriseScorerReturnsBoundedValue() {
        var scorer = new SurpriseScorer();
        var memory = scored("task", "solution",
                Map.of("type", FeatureValue.string("routine")));
        assertThat(scorer.score(memory, Instant.now())).isBetween(0.0, 1.0);
    }

    @Test
    void surpriseScorerReturnsDefaultForEmptyFeatures() {
        var scorer = new SurpriseScorer();
        var memory = scored("task", "solution", Map.of());
        assertThat(scorer.score(memory, Instant.now())).isEqualTo(0.5);
    }

    @Test
    void compositeWeightedMean() {
        ImportanceScorer fixed80 = (m, now) -> 0.8;
        ImportanceScorer fixed20 = (m, now) -> 0.2;
        var composite = new CompositeImportanceScorer(List.of(
                new WeightedScorer(fixed80, 3.0),
                new WeightedScorer(fixed20, 1.0)));
        var memory = scored("p", "s", Map.of("k", FeatureValue.string("v")));
        double score = composite.score(memory, Instant.now());
        assertThat(score).isCloseTo(0.65, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void compositeSingleScorerReturnsItsValue() {
        ImportanceScorer fixed = (m, now) -> 0.42;
        var composite = new CompositeImportanceScorer(List.of(
                new WeightedScorer(fixed, 1.0)));
        var memory = scored("p", "s", Map.of("k", FeatureValue.string("v")));
        assertThat(composite.score(memory, Instant.now())).isCloseTo(0.42,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void compositeRejectsEmptyList() {
        assertThatThrownBy(() -> new CompositeImportanceScorer(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weightedScorerRejectsZeroWeight() {
        assertThatThrownBy(() -> new WeightedScorer((m, now) -> 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weightedScorerRejectsNegativeWeight() {
        assertThatThrownBy(() -> new WeightedScorer((m, now) -> 0.5, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
