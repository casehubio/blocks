package io.casehub.blocks.summarisation.examples.logistics;

import io.casehub.blocks.summarisation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

class LogisticsPipelineTest {

    static final EventLevel L1_SCANS = new EventLevel("scans", 1);
    static final EventLevel L2_ANOMALIES = new EventLevel("anomalies", 2);
    static final EventLevel L3_PHASES = new EventLevel("hub-phases", 3);
    static final EventLevel L4_NARRATIVE = new EventLevel("narrative", 4);

    EventStreamBus<PackageScan> scanBus;
    EventStreamBus<PackageAnomaly> anomalyBus;
    EventStreamBus<HubPhase> phaseBus;
    EventStreamBus<HubNarrative> narrativeBus;

    SummarisationRunner<PackageScan, PackageAnomaly> anomalyRunner;
    SummarisationRunner<PackageAnomaly, HubPhase> phaseRunner;
    SummarisationRunner<HubPhase, HubNarrative> narrativeRunner;

    List<PackageAnomaly> capturedAnomalies;
    List<HubPhase> capturedPhases;
    List<HubNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        scanBus = new EventStreamBus<>();
        anomalyBus = new EventStreamBus<>();
        phaseBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedAnomalies = new ArrayList<>();
        capturedPhases = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        anomalyBus.subscribe(a -> true, e -> capturedAnomalies.add(e.payload()));
        phaseBus.subscribe(p -> true, e -> capturedPhases.add(e.payload()));
        narrativeBus.subscribe(n -> true, e -> capturedNarratives.add(e.payload()));

        anomalyRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 10),
            Summariser.ofSync(new AnomalyDetectorSummariser()),
            anomalyBus, L2_ANOMALIES);

        phaseRunner = new SummarisationRunner<>(
            new WindowPolicy(300_000, 0),
            Summariser.ofSync(new HubPhaseSummariser()),
            phaseBus, L3_PHASES);

        Summariser<HubPhase, HubNarrative> narrativeSummariser = batch -> {
            String phases = batch.stream()
                .map(e -> e.payload().phase())
                .distinct()
                .reduce((a, b) -> a + " → " + b)
                .orElse("none");
            long ts = batch.get(batch.size() - 1).timestamp();
            return CompletableFuture.completedFuture(List.of(
                new HubNarrative("Hub progression: " + phases, ts)));
        };

        narrativeRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 2),
            narrativeSummariser,
            narrativeBus, L4_NARRATIVE);

        // Wire pipeline
        scanBus.subscribe(s -> true, anomalyRunner::collect);
        anomalyBus.subscribe(a -> true, phaseRunner::collect);
        phaseBus.subscribe(p -> true, narrativeRunner::collect);
    }

    @Test
    void fullPipeline_scansToNarrative() {
        // Feed 10 scans with misroute pattern → triggers L1->L2
        for (int i = 0; i < 8; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-1", ScanType.INBOUND, i * 100L);
        }
        feedScan("PKG-8", "WH-B", 10.0, "DEST-1", ScanType.INBOUND, 800);
        feedScan("PKG-9", "WH-C", 10.0, "DEST-1", ScanType.INBOUND, 900);

        anomalyRunner.tick(1000);

        assertThat(capturedAnomalies)
            .extracting(PackageAnomaly::type)
            .contains(AnomalyType.MISROUTE);
        assertThat(capturedAnomalies)
            .extracting(PackageAnomaly::rationale)
            .allMatch(r -> r != null && !r.isEmpty());
    }

    @Test
    void weightAnomaly_detected() {
        for (int i = 0; i < 9; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.OUTBOUND, i * 100L);
        }
        feedScan("PKG-HEAVY", "WH-A", 75.0, "DEST-X", ScanType.OUTBOUND, 900);

        anomalyRunner.tick(1000);

        assertThat(capturedAnomalies)
            .extracting(PackageAnomaly::type)
            .contains(AnomalyType.WEIGHT_MISMATCH);
    }

    @Test
    void phaseClassification_normalVsCongestion() {
        // First window — one medium-severity anomaly → normal flow
        for (int i = 0; i < 9; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.INBOUND, i * 100L);
        }
        feedScan("PKG-HEAVY", "WH-A", 55.0, "DEST-X", ScanType.INBOUND, 900);  // MEDIUM weight anomaly
        anomalyRunner.tick(1000);
        phaseRunner.tick(400_000);

        assertThat(capturedPhases).hasSize(1);
        assertThat(capturedPhases.get(0).phase()).isEqualTo("NORMAL_FLOW");
    }

    @Test
    void patternB_complianceAccumulatorAlongsidePipeline() {
        // Pattern B: direct accumulator for compliance audit log
        var complianceAccumulator = new EventAccumulator<PackageAnomaly>(new WindowPolicy(600_000, 0));
        anomalyBus.subscribe(a -> true, complianceAccumulator::collect);

        // Feed 10 scans with a heavy package
        for (int i = 0; i < 9; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.TRANSFER, i * 100L);
        }
        feedScan("PKG-HEAVY", "WH-A", 60.0, "DEST-X", ScanType.TRANSFER, 900);
        anomalyRunner.tick(1000);

        // Pipeline produced anomalies
        assertThat(capturedAnomalies).isNotEmpty();

        // Pattern B accumulator also collected (time-based window, not yet triggered)
        assertThat(complianceAccumulator.size()).isGreaterThan(0);
        assertThat(complianceAccumulator.shouldEmit(500_000)).isFalse();
        assertThat(complianceAccumulator.shouldEmit(700_000)).isTrue();
    }

    @Test
    void multiLevelSubscription_singleConsumerOnTwoBuses() {
        List<String> allEvents = new ArrayList<>();
        anomalyBus.subscribe(a -> true, e -> allEvents.add("L2:" + e.payload().type()));
        phaseBus.subscribe(p -> true, e -> allEvents.add("L3:" + e.payload().phase()));

        // Feed enough to produce L2 and L3
        for (int i = 0; i < 10; i++) {
            feedScan("PKG-" + i, "WH-A", (i == 9 ? 60.0 : 10.0), "DEST-" + i, ScanType.INBOUND, i * 100L);
        }
        anomalyRunner.tick(1000);
        phaseRunner.tick(400_000);

        assertThat(allEvents).anyMatch(s -> s.startsWith("L2:"));
        assertThat(allEvents).anyMatch(s -> s.startsWith("L3:"));
    }

    // --- Branch coverage: AnomalyDetectorSummariser ---

    @Test
    void anomalyDetection_bothMisrouteAndWeightInSameBatch() {
        feedScan("PKG-0", "WH-A", 10.0, "DEST-1", ScanType.INBOUND, 0);
        feedScan("PKG-1", "WH-B", 10.0, "DEST-1", ScanType.INBOUND, 100);
        feedScan("PKG-2", "WH-A", 55.0, "DEST-2", ScanType.OUTBOUND, 200);
        feedScan("PKG-3", "WH-A", 10.0, "DEST-3", ScanType.INBOUND, 300);
        feedScan("PKG-4", "WH-A", 10.0, "DEST-4", ScanType.INBOUND, 400);
        feedScan("PKG-5", "WH-A", 10.0, "DEST-5", ScanType.INBOUND, 500);
        feedScan("PKG-6", "WH-A", 10.0, "DEST-6", ScanType.INBOUND, 600);
        feedScan("PKG-7", "WH-A", 10.0, "DEST-7", ScanType.INBOUND, 700);
        feedScan("PKG-8", "WH-A", 10.0, "DEST-8", ScanType.INBOUND, 800);
        feedScan("PKG-9", "WH-A", 10.0, "DEST-9", ScanType.INBOUND, 900);

        anomalyRunner.tick(1000);

        assertThat(capturedAnomalies)
            .extracting(PackageAnomaly::type)
            .contains(AnomalyType.MISROUTE, AnomalyType.WEIGHT_MISMATCH);
    }

    @Test
    void anomalyDetection_noAnomalies_producesEmptyList() {
        for (int i = 0; i < 10; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.INBOUND, i * 100L);
        }
        anomalyRunner.tick(1000);

        assertThat(capturedAnomalies).isEmpty();
    }

    // --- Branch coverage: HubPhaseSummariser ---

    @Test
    void phaseClassification_congestion_threeOrMoreHighSeverity() {
        feedScan("PKG-0", "WH-A", 10.0, "DEST-1", ScanType.INBOUND, 0);
        feedScan("PKG-1", "WH-B", 10.0, "DEST-1", ScanType.INBOUND, 100);
        feedScan("PKG-2", "WH-C", 10.0, "DEST-1", ScanType.INBOUND, 200);
        feedScan("PKG-3", "WH-A", 10.0, "DEST-2", ScanType.INBOUND, 300);
        feedScan("PKG-4", "WH-B", 10.0, "DEST-2", ScanType.INBOUND, 400);
        feedScan("PKG-5", "WH-C", 10.0, "DEST-2", ScanType.INBOUND, 500);
        feedScan("PKG-6", "WH-A", 10.0, "DEST-3", ScanType.INBOUND, 600);
        feedScan("PKG-7", "WH-B", 10.0, "DEST-3", ScanType.INBOUND, 700);
        feedScan("PKG-8", "WH-C", 10.0, "DEST-3", ScanType.INBOUND, 800);
        feedScan("PKG-9", "WH-A", 10.0, "DEST-4", ScanType.INBOUND, 900);
        anomalyRunner.tick(1000);

        assertThat(capturedAnomalies)
            .filteredOn(a -> "HIGH".equals(a.severity()))
            .hasSizeGreaterThanOrEqualTo(3);

        phaseRunner.tick(400_000);

        assertThat(capturedPhases).hasSize(1);
        assertThat(capturedPhases.get(0).phase()).isEqualTo("CONGESTION");
        assertThat(capturedPhases.get(0).rationale()).contains("high-severity");
    }

    @Test
    void phaseClassification_emptyAnomalyBatch_noPhaseProduced() {
        for (int i = 0; i < 10; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.INBOUND, i * 100L);
        }
        anomalyRunner.tick(1000);
        assertThat(capturedAnomalies).as("no anomalies").isEmpty();

        phaseRunner.tick(400_000);
        assertThat(capturedPhases).as("no anomalies → no phase").isEmpty();
    }

    private void feedScan(String scanId, String warehouseId, double weight,
                          String destination, ScanType scanType, long timestamp) {
        scanBus.publish(new LevelEvent<>(
            new PackageScan(scanId, warehouseId, weight, destination, scanType),
            timestamp, L1_SCANS));
    }
}
