package io.casehub.blocks.summarisation.examples.logistics;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnomalyDetectorSummariser implements Summariser.SyncSummariser<PackageScan, PackageAnomaly> {

    @Override
    public List<PackageAnomaly> summarise(List<LevelEvent<PackageScan>> batch) {
        var anomalies = new ArrayList<PackageAnomaly>();

        // Detect misroutes: same destination appearing from multiple warehouses
        Map<String, List<PackageScan>> byDestination = batch.stream()
            .collect(Collectors.groupingBy(
                e -> e.payload().destination(),
                Collectors.mapping(LevelEvent::payload, Collectors.toList())));

        for (var entry : byDestination.entrySet()) {
            long distinctWarehouses = entry.getValue().stream()
                .map(PackageScan::warehouseId)
                .distinct().count();
            if (distinctWarehouses > 1) {
                anomalies.add(new PackageAnomaly(
                    AnomalyType.MISROUTE, "HIGH",
                    entry.getKey() + " routed from " + distinctWarehouses + " warehouses",
                    "Same destination from multiple origins suggests misroute"));
            }
        }

        // Detect weight anomalies: any package over 50kg flagged
        for (var e : batch) {
            if (e.payload().weight() > 50.0) {
                anomalies.add(new PackageAnomaly(
                    AnomalyType.WEIGHT_MISMATCH, "MEDIUM",
                    e.payload().scanId() + " weighs " + e.payload().weight() + "kg",
                    "Weight exceeds 50kg threshold"));
            }
        }

        return anomalies;
    }
}
