package io.casehub.blocks.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoundationTypesTest {

    @Test
    void retentionScoreComputation() {
        var config = new RetentionConfig(0.1, 1.0, 1.0, 0.5, 0.5);
        var score = RetentionScore.compute("c1", "e1", 0.8, 0.6, 1.0, 0.9, config);
        assertThat(score.composite()).isBetween(0.0, 1.0);
        assertThat(score.caseId()).isEqualTo("c1");
        assertThat(score.entityId()).isEqualTo("e1");
        assertThat(score.confidence()).isEqualTo(0.8);
        assertThat(score.recencyFactor()).isEqualTo(0.6);
    }

    @Test
    void retentionScoreWeightedMean() {
        var config = new RetentionConfig(0.1, 2.0, 1.0, 0.0, 0.0);
        var score = RetentionScore.compute("c1", "e1", 0.6, 0.3, 0.5, 0.5, config);
        // (0.6*2 + 0.3*1 + 0.5*0 + 0.5*0) / (2+1+0+0) = 1.5/3 = 0.5
        assertThat(score.composite()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void retentionConfigValidatesAllWeightsZero() {
        assertThatThrownBy(() -> new RetentionConfig(0.1, 0.0, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retentionConfigRejectsNegativeThreshold() {
        assertThatThrownBy(() -> new RetentionConfig(-0.1, 1.0, 1.0, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retentionConfigRejectsThresholdAboveOne() {
        assertThatThrownBy(() -> new RetentionConfig(1.1, 1.0, 1.0, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retentionConfigDefaultHasExpectedValues() {
        assertThat(RetentionConfig.DEFAULT.retentionThreshold()).isEqualTo(0.1);
        assertThat(RetentionConfig.DEFAULT.confidenceWeight()).isEqualTo(1.0);
    }

    @Test
    void hygieneTickSealedVariants() {
        HygieneTick idle = new HygieneTick.Idle("no memories");
        HygieneTick completed = new HygieneTick.Completed(2, 3, 10, List.of());
        HygieneTick failed = new HygieneTick.Failed("store error");
        assertThat(idle).isInstanceOf(HygieneTick.Idle.class);
        assertThat(completed).isInstanceOf(HygieneTick.Completed.class);
        assertThat(failed).isInstanceOf(HygieneTick.Failed.class);
        assertThat(((HygieneTick.Completed) completed).consolidated()).isEqualTo(2);
        assertThat(((HygieneTick.Completed) completed).evicted()).isEqualTo(3);
        assertThat(((HygieneTick.Completed) completed).totalScored()).isEqualTo(10);
    }

    @Test
    void maintenanceTickSealedVariants() {
        var hygiene = new HygieneTick.Completed(0, 0, 5, List.of());
        var completed = new MaintenanceTick.Completed(hygiene, 3, 1, List.of());
        var failed = new MaintenanceTick.Failed("reflection", "timeout");
        assertThat(completed.hygiene()).isEqualTo(hygiene);
        assertThat(completed.reflectionsGenerated()).isEqualTo(3);
        assertThat(completed).isInstanceOf(MaintenanceTick.class);
        assertThat(failed.stage()).isEqualTo("reflection");
        assertThat(failed.reason()).isEqualTo("timeout");
        assertThat(failed).isInstanceOf(MaintenanceTick.class);
    }

    @Test
    void hygieneEventSealedVariants() {
        var score = RetentionScore.compute("c1", "e1", 0.1, 0.1, 0.1, 0.1,
                                           new RetentionConfig(0.5, 1.0, 1.0, 0.5, 0.5));
        HygieneEvent evicted = new HygieneEvent.MemoryEvicted("c1", score);
        HygieneEvent consolidated = new HygieneEvent.MemoryConsolidated("m1", List.of("s1", "s2"));
        HygieneEvent reflected = new HygieneEvent.ReflectionGenerated("a1", "insight");
        var violation = new IntegrityViolation("c1", ViolationType.ORPHANED_SUPERSESSION, "detail", false);
        HygieneEvent detected = new HygieneEvent.IntegrityViolationDetected(violation);
        assertThat(evicted).isInstanceOf(HygieneEvent.MemoryEvicted.class);
        assertThat(consolidated).isInstanceOf(HygieneEvent.MemoryConsolidated.class);
        assertThat(reflected).isInstanceOf(HygieneEvent.ReflectionGenerated.class);
        assertThat(detected).isInstanceOf(HygieneEvent.IntegrityViolationDetected.class);
    }

    @Test
    void reflectionEntryCarriesProvenance() {
        var entry = new ReflectionEntry("agent-1", "tenant-1", "insight text",
                Instant.now(), List.of("case-1", "case-2"));
        assertThat(entry.sourceCaseIds()).containsExactly("case-1", "case-2");
        assertThat(entry.agentId()).isEqualTo("agent-1");
    }

    @Test
    void reflectionEntryDefaultsNullSourceCaseIds() {
        var entry = new ReflectionEntry("a", "t", "i", Instant.now(), null);
        assertThat(entry.sourceCaseIds()).isEmpty();
    }

    @Test
    void noOpReflectionStoreAcceptsWithoutError() {
        var store = new NoOpReflectionStore();
        store.store(new ReflectionEntry("a", "t", "i", Instant.now(), List.of()));
    }

    @Test
    void noOpSemanticIntegrityCheckerReturnsEmpty() {
        var checker = new NoOpSemanticIntegrityChecker();
        assertThat(checker.checkSemantic(List.of(), "a", "t")).isEmpty();
    }

    @Test
    void violationTypeCoversAllExpected() {
        assertThat(ViolationType.values()).containsExactlyInAnyOrder(
                ViolationType.ORPHANED_SUPERSESSION,
                ViolationType.DUPLICATE_CASE,
                ViolationType.MISSING_FEATURES,
                ViolationType.UNPROCESSED_STALE,
                ViolationType.SEMANTIC_CONFLICT);
    }

    @Test
    void integrityViolationRejectsNulls() {
        assertThatThrownBy(() -> new IntegrityViolation(null, ViolationType.DUPLICATE_CASE, "d", false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrityViolation("c1", null, "d", false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrityViolation("c1", ViolationType.DUPLICATE_CASE, null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
