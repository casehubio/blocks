package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.experience.ScoreableContent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfidenceScorerTest {

    private static CbrMatch<CbrRecord> scored(String problem, String solution,
                                                  Map<String, FeatureValue> features) {
        var c = new CbrFeatureRecord(problem, solution, null, null, features, null, null);
        return new CbrMatch<>(c, "case-1", 0.5);
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
        var c = new CbrFeatureRecord("test problem", "n/a", null, null, Map.of(), null, null);
        var memory = new CbrMatch<CbrRecord>(c, "case-1", 0.5);
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
        ConfidenceScorer fixed80 = (m, now) -> 0.8;
        ConfidenceScorer fixed20 = (m, now) -> 0.2;
        var composite = new CompositeConfidenceScorer(List.of(
                new WeightedScorer(fixed80, 3.0),
                new WeightedScorer(fixed20, 1.0)));
        var memory = scored("p", "s", Map.of("k", FeatureValue.string("v")));
        double score = composite.score(memory, Instant.now());
        assertThat(score).isCloseTo(0.65, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void compositeSingleScorerReturnsItsValue() {
        ConfidenceScorer fixed = (m, now) -> 0.42;
        var composite = new CompositeConfidenceScorer(List.of(
                new WeightedScorer(fixed, 1.0)));
        var memory = scored("p", "s", Map.of("k", FeatureValue.string("v")));
        assertThat(composite.score(memory, Instant.now())).isCloseTo(0.42,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void compositeRejectsEmptyList() {
        assertThatThrownBy(() -> new CompositeConfidenceScorer(List.of()))
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

    @Test
    void surpriseScorer_contentScorer_emptyMetadata() {
        var scorer  = new SurpriseScorer();
        var content = new ScoreableContent("text", Map.of(), Instant.EPOCH);
        assertThat(scorer.score(content)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void surpriseScorer_contentScorer_withMetadata() {
        var scorer = new SurpriseScorer();
        var content = new ScoreableContent("text",
                                           Map.of("feature1", "longvalue12345", "feature2", "another"),
                                           Instant.EPOCH);
        double score = scorer.score(content);
        assertThat(score).isBetween(0.0, 1.0);
        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    void arousalScorer_contentScorer_noHighArousalWords() {
        var scorer  = new ArousalScorer();
        var content = new ScoreableContent("a peaceful calm day", Map.of(), Instant.EPOCH);
        assertThat(scorer.score(content)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void arousalScorer_contentScorer_withHighArousalWords() {
        var scorer = new ArousalScorer();
        var content = new ScoreableContent("critical emergency alert failure",
                                           Map.of(), Instant.EPOCH);
        double score = scorer.score(content);
        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isLessThanOrEqualTo(1.0);
    }

    @Test
    void dualInterface_surpriseScorer_consistency() {
        var    scorer       = new SurpriseScorer();
        Map<String, FeatureValue> features = Map.of("key", FeatureValue.string("value"));
        var    cbrCase      = new CbrFeatureRecord("problem", "solution", null, null, features, null, null);
        var    scored       = new CbrMatch<CbrRecord>(cbrCase, "case-1", 0.5);
        var    now          = Instant.now();
        double cbrScore     = scorer.score(scored, now);
        var    content      = new ScoreableContent("text", Map.of("key", "value"), Instant.EPOCH);
        double contentScore = scorer.score(content);
        assertThat(cbrScore).isBetween(0.0, 1.0);
        assertThat(contentScore).isBetween(0.0, 1.0);
    }

    @Test
    void dualInterface_arousalScorer_consistency() {
        var    scorer       = new ArousalScorer();
        var    cbrCase      = new CbrFeatureRecord("critical error", "urgent fix", null, null, Map.of(), null, null);
        var    scored       = new CbrMatch<CbrRecord>(cbrCase, "case-1", 0.5);
        double cbrScore     = scorer.score(scored, Instant.now());
        var    content      = new ScoreableContent("critical error urgent fix", Map.of(), Instant.EPOCH);
        double contentScore = scorer.score(content);
        assertThat(cbrScore).isCloseTo(contentScore, org.assertj.core.data.Offset.offset(0.001));
    }

}
