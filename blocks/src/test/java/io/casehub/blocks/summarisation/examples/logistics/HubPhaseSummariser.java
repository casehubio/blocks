package io.casehub.blocks.summarisation.examples.logistics;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.List;

public class HubPhaseSummariser implements Summariser.SyncSummariser<PackageAnomaly, HubPhase> {

    @Override
    public List<HubPhase> summarise(List<LevelEvent<PackageAnomaly>> batch) {
        if (batch.isEmpty()) return List.of();

        long earliest = batch.get(0).timestamp();
        long latest = batch.get(batch.size() - 1).timestamp();
        long duration = latest - earliest;

        long highCount = batch.stream()
            .filter(e -> "HIGH".equals(e.payload().severity()))
            .count();

        String phase;
        String rationale;

        if (highCount >= 3) {
            phase = "CONGESTION";
            rationale = highCount + " high-severity anomalies — hub overwhelmed";
        } else if (highCount >= 1) {
            phase = "DEGRADED";
            rationale = highCount + " high-severity anomaly — monitoring";
        } else {
            phase = "NORMAL_FLOW";
            rationale = "No high-severity anomalies";
        }

        return List.of(new HubPhase(phase, duration, rationale));
    }
}
