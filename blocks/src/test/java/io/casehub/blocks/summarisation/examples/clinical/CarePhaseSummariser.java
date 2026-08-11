package io.casehub.blocks.summarisation.examples.clinical;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.List;

public class CarePhaseSummariser implements Summariser.SyncSummariser<ClinicalEvent, CarePhase> {

    @Override
    public List<CarePhase> summarise(List<LevelEvent<ClinicalEvent>> batch) {
        if (batch.isEmpty()) return List.of();

        long earliest = batch.get(0).timestamp();
        long latest = batch.get(batch.size() - 1).timestamp();
        long duration = latest - earliest;

        long criticalCount = batch.stream()
            .filter(e -> e.payload().severity() == Severity.CRITICAL)
            .count();

        String phase;
        String rationale;

        if (criticalCount >= 2) {
            phase = "ACUTE_DETERIORATION";
            rationale = criticalCount + " critical events in window";
        } else if (criticalCount == 1) {
            phase = "EARLY_WARNING";
            rationale = "Single critical event — monitoring closely";
        } else {
            phase = "STABLE_MONITORING";
            rationale = "No critical events — routine monitoring";
        }

        return List.of(new CarePhase(phase, duration, rationale));
    }
}
