package io.casehub.blocks.prompt;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VariantSelectorTest {

    private VariantSelector selector(double ratio, int cbThreshold) {
        return new VariantSelector(ratio, cbThreshold);
    }

    @Test
    void sameCaseAndCapabilityAlwaysReturnsSameSlot() {
        var sel = selector(0.5, 5);
        var caseId = UUID.randomUUID();
        var slot1 = sel.selectSlot(caseId, "triage");
        var slot2 = sel.selectSlot(caseId, "triage");
        assertThat(slot1).isEqualTo(slot2);
    }

    @Test
    void zeroRatioAlwaysReturnsControl() {
        var sel = selector(0.0, 5);
        for (int i = 0; i < 100; i++) {
            assertThat(sel.selectSlot(UUID.randomUUID(), "cap")).isEqualTo("control");
        }
    }

    @Test
    void fullRatioAlwaysReturnsExperiment() {
        var sel = selector(1.0, 5);
        for (int i = 0; i < 100; i++) {
            assertThat(sel.selectSlot(UUID.randomUUID(), "cap")).isEqualTo("experiment");
        }
    }

    @Test
    void distributionConvergesToConfiguredRatio() {
        var sel = selector(0.1, 5);
        Map<String, Integer> counts = new HashMap<>();
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            var slot = sel.selectSlot(UUID.randomUUID(), "cap");
            counts.merge(slot, 1, Integer::sum);
        }
        double experimentRatio = counts.getOrDefault("experiment", 0) / (double) total;
        assertThat(experimentRatio).isBetween(0.05, 0.15);
    }

    @Test
    void circuitBreakerTripsAfterConsecutiveFailures() {
        var sel = selector(1.0, 3);
        sel.recordOutcome("cap", false);
        sel.recordOutcome("cap", false);
        sel.recordOutcome("cap", false);
        assertThat(sel.selectSlot(UUID.randomUUID(), "cap")).isEqualTo("control");
    }

    @Test
    void circuitBreakerResetsOnSuccess() {
        var sel = selector(1.0, 3);
        sel.recordOutcome("cap", false);
        sel.recordOutcome("cap", false);
        sel.recordOutcome("cap", true);
        assertThat(sel.selectSlot(UUID.randomUUID(), "cap")).isEqualTo("experiment");
    }

    @Test
    void circuitBreakerIsPerCapability() {
        var sel = selector(1.0, 2);
        sel.recordOutcome("cap-a", false);
        sel.recordOutcome("cap-a", false);
        assertThat(sel.selectSlot(UUID.randomUUID(), "cap-a")).isEqualTo("control");
        assertThat(sel.selectSlot(UUID.randomUUID(), "cap-b")).isEqualTo("experiment");
    }

    @Test
    void onlyReturnsControlOrExperiment() {
        var sel = selector(0.5, 5);
        for (int i = 0; i < 1000; i++) {
            var slot = sel.selectSlot(UUID.randomUUID(), "cap");
            assertThat(slot).isIn("control", "experiment");
        }
    }
}
