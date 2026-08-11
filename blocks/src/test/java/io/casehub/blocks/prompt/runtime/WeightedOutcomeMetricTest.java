package io.casehub.blocks.prompt.runtime;

import io.casehub.blocks.prompt.VariantOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedOutcomeMetricTest {

    private VariantOutcome outcome(String result) {
        return new VariantOutcome("v1", "sig", result, 0.8, null, Instant.now());
    }

    @Test
    void allSuccessScoresOne() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of(outcome("SUCCESS"), outcome("SUCCESS")))).isEqualTo(1.0);
    }

    @Test
    void allFailureScoresZero() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of(outcome("FAILURE"), outcome("FAILURE")))).isEqualTo(0.0);
    }

    @Test
    void mixedOutcomesWeightedCorrectly() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of(outcome("SUCCESS"), outcome("FAILURE")))).isEqualTo(0.5);
    }

    @Test
    void gateExpiredWeightedAtPointFive() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of(outcome("GATE_EXPIRED")))).isEqualTo(0.5);
    }

    @Test
    void gateRejectedWeightedAtPointTwoFive() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of(outcome("GATE_REJECTED")))).isEqualTo(0.25);
    }

    @Test
    void declinedWeightedAtZero() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of(outcome("DECLINED")))).isEqualTo(0.0);
    }

    @Test
    void unknownOutcomeDefaultsToZero() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of(outcome("SOME_UNKNOWN_VALUE")))).isEqualTo(0.0);
    }

    @Test
    void emptyListScoresZero() {
        var metric = new WeightedOutcomeMetric();
        assertThat(metric.score(List.of())).isEqualTo(0.0);
    }
}
