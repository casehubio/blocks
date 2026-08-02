package io.casehub.blocks.summarisation.examples.clinical;

import io.casehub.blocks.summarisation.EventAccumulator;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.WindowPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalPipelineTest {

    static final EventLevel L1_VITALS = new EventLevel("vitals", 1);
    static final EventLevel L2_EVENTS = new EventLevel("clinical-events", 2);
    static final EventLevel L3_PHASES = new EventLevel("care-phases", 3);
    static final EventLevel L4_NARRATIVE = new EventLevel("narrative", 4);

    EventStreamBus<VitalReading> vitalsBus;
    EventStreamBus<ClinicalEvent> eventBus;
    EventStreamBus<CarePhase> phaseBus;
    EventStreamBus<ClinicalNarrative> narrativeBus;

    SummarisationRunner<VitalReading, ClinicalEvent> eventRunner;
    SummarisationRunner<ClinicalEvent, CarePhase> phaseRunner;
    SummarisationRunner<CarePhase, ClinicalNarrative> narrativeRunner;

    List<ClinicalEvent> capturedEvents;
    List<CarePhase> capturedPhases;
    List<ClinicalNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        vitalsBus = new EventStreamBus<>();
        eventBus = new EventStreamBus<>();
        phaseBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedEvents = new ArrayList<>();
        capturedPhases = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        eventBus.subscribe(e -> true, e -> capturedEvents.add(e.payload()));
        phaseBus.subscribe(p -> true, e -> capturedPhases.add(e.payload()));
        narrativeBus.subscribe(n -> true, e -> capturedNarratives.add(e.payload()));

        eventRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 5),
            Summariser.ofSync(new ClinicalEventSummariser()),
            eventBus, L2_EVENTS);

        phaseRunner = new SummarisationRunner<>(
            new WindowPolicy(900_000, 0),
            Summariser.ofSync(new CarePhaseSummariser()),
            phaseBus, L3_PHASES);

        Summariser<CarePhase, ClinicalNarrative> narrativeSummariser = batch -> {
            String phases = batch.stream()
                .map(e -> e.payload().phase())
                .distinct()
                .reduce((a, b) -> a + " → " + b)
                .orElse("none");
            long ts = batch.get(batch.size() - 1).timestamp();
            return CompletableFuture.completedFuture(List.of(
                new ClinicalNarrative("Patient progressed through: " + phases, ts)));
        };

        narrativeRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 3),
            narrativeSummariser,
            narrativeBus, L4_NARRATIVE);

        // Wire pipeline
        vitalsBus.subscribe(v -> true, eventRunner::collect);
        eventBus.subscribe(e -> true, phaseRunner::collect);
        phaseBus.subscribe(p -> true, narrativeRunner::collect);
    }

    @Test
    void fullPipeline_vitalsToNarrative() {
        // Feed 5 vitals with 2 critical readings → triggers L1->L2
        feedVital(VitalType.HR, 72, "bpm", 1000);
        feedVital(VitalType.SPO2, 98, "%", 2000);
        feedVital(VitalType.HR, 135, "bpm", 3000);   // CRITICAL tachycardia
        feedVital(VitalType.SPO2, 88, "%", 4000);     // CRITICAL hypoxemia
        feedVital(VitalType.HR, 110, "bpm", 5000);    // MODERATE tachycardia

        eventRunner.tick(6000);

        assertThat(capturedEvents)
            .extracting(ClinicalEvent::category)
            .containsExactly(
                ClinicalCategory.TACHYCARDIA,
                ClinicalCategory.HYPOXEMIA,
                ClinicalCategory.TACHYCARDIA);
        assertThat(capturedEvents)
            .extracting(ClinicalEvent::rationale)
            .allMatch(r -> r != null && !r.isEmpty());
    }

    @Test
    void phaseTransition_stableToDeterioration() {
        // First window — stable (moderate events, no critical)
        feedVital(VitalType.HR, 75, "bpm", 1000);
        feedVital(VitalType.HR, 105, "bpm", 2000);  // MODERATE tachycardia
        feedVital(VitalType.SPO2, 93, "%", 3000);   // MODERATE hypoxemia
        feedVital(VitalType.HR, 80, "bpm", 4000);
        feedVital(VitalType.SPO2, 96, "%", 5000);
        eventRunner.tick(6000);

        // Trigger L2->L3 with time window
        phaseRunner.tick(1_000_000);  // past 900s window

        assertThat(capturedPhases).hasSize(1);
        assertThat(capturedPhases.get(0).phase()).isEqualTo("STABLE_MONITORING");

        // Second window — deterioration (critical events)
        feedVital(VitalType.HR, 140, "bpm", 1_000_001);
        feedVital(VitalType.SPO2, 85, "%", 1_000_002);
        feedVital(VitalType.HR, 145, "bpm", 1_000_003);
        feedVital(VitalType.SPO2, 82, "%", 1_000_004);
        feedVital(VitalType.HR, 150, "bpm", 1_000_005);
        eventRunner.tick(1_000_006);

        phaseRunner.tick(2_000_000);

        assertThat(capturedPhases).hasSize(2);
        assertThat(capturedPhases.get(1).phase()).isEqualTo("ACUTE_DETERIORATION");
    }

    @Test
    void asyncNarrative_producesNarrativeFromPhases() {
        // Feed enough to produce 3 phases → triggers L3->L4
        for (int i = 0; i < 3; i++) {
            feedVital(VitalType.HR, 72, "bpm", i * 1_000_000L + 1);
            feedVital(VitalType.HR, 73, "bpm", i * 1_000_000L + 2);
            feedVital(VitalType.HR, 105, "bpm", i * 1_000_000L + 3);  // MODERATE to trigger event
            feedVital(VitalType.HR, 75, "bpm", i * 1_000_000L + 4);
            feedVital(VitalType.HR, 76, "bpm", i * 1_000_000L + 5);
            eventRunner.tick(i * 1_000_000L + 6);
            phaseRunner.tick((i + 1) * 1_000_000L);
        }

        narrativeRunner.tick(3_000_001);

        assertThat(capturedNarratives).hasSize(1);
        assertThat(capturedNarratives.get(0).summary()).contains("STABLE_MONITORING");
    }

    @Test
    void asyncError_loggedAndDroppedByDefault() {
        Summariser<CarePhase, ClinicalNarrative> failingSummariser = batch ->
                                                                             CompletableFuture.failedFuture(new RuntimeException("LLM unavailable"));

        var failingRunner = new SummarisationRunner<>(
                new WindowPolicy(0, 1), failingSummariser, narrativeBus, L4_NARRATIVE);

        failingRunner.collect(new LevelEvent<>(
                new CarePhase("STABLE", 1000, "test"), 1, L3_PHASES));

        CompletionStage<Void> result = failingRunner.tick(2);

        assertThat(result.toCompletableFuture().isCompletedExceptionally())
                .as("failure swallowed — logged and dropped").isFalse();
    }

    @Test
    void fanOut_patternBAccumulatorAlongsidePipeline() {
        // Pattern B: direct EventAccumulator on L2 bus (medication alert check)
        var alertAccumulator = new EventAccumulator<ClinicalEvent>(new WindowPolicy(0, 3));
        eventBus.subscribe(
            e -> e.severity() == Severity.CRITICAL,
            e -> alertAccumulator.collect(e));

        // Feed 5 vitals with 3 critical → triggers both pipeline and accumulator
        feedVital(VitalType.HR, 140, "bpm", 1000);
        feedVital(VitalType.SPO2, 85, "%", 2000);
        feedVital(VitalType.HR, 145, "bpm", 3000);
        feedVital(VitalType.SPO2, 82, "%", 4000);
        feedVital(VitalType.HR, 150, "bpm", 5000);
        eventRunner.tick(6000);

        // Pipeline produced L2 events
        assertThat(capturedEvents).hasSizeGreaterThanOrEqualTo(3);

        // Pattern B accumulator also collected critical events
        assertThat(alertAccumulator.shouldEmit(7000)).isTrue();
        var alertBatch = alertAccumulator.drain();
        assertThat(alertBatch)
            .extracting(e -> e.payload().severity())
            .containsOnly(Severity.CRITICAL);
    }

    // --- Branch coverage: ClinicalEventSummariser ---

    @Test
    void clinicalClassification_bradycardia() {
        feedVital(VitalType.HR, 45, "bpm", 1000);
        feedVital(VitalType.HR, 72, "bpm", 2000);
        feedVital(VitalType.HR, 48, "bpm", 3000);
        feedVital(VitalType.HR, 75, "bpm", 4000);
        feedVital(VitalType.HR, 42, "bpm", 5000);
        eventRunner.tick(6000);

        assertThat(capturedEvents)
            .filteredOn(e -> e.category() == ClinicalCategory.BRADYCARDIA)
            .hasSize(3)
            .allMatch(e -> e.severity() == Severity.MODERATE)
            .allMatch(e -> e.rationale().contains("HR < 50"));
    }

    @Test
    void clinicalClassification_moderateHypoxemia() {
        feedVital(VitalType.SPO2, 92, "%", 1000);
        feedVital(VitalType.SPO2, 93, "%", 2000);
        feedVital(VitalType.SPO2, 91, "%", 3000);
        feedVital(VitalType.SPO2, 97, "%", 4000);
        feedVital(VitalType.SPO2, 96, "%", 5000);
        eventRunner.tick(6000);

        assertThat(capturedEvents)
            .filteredOn(e -> e.category() == ClinicalCategory.HYPOXEMIA
                         && e.severity() == Severity.MODERATE)
            .hasSize(3)
            .allMatch(e -> e.rationale().contains("SpO2 < 94"));
    }

    @Test
    void clinicalClassification_hypertension() {
        feedVital(VitalType.BP_SYSTOLIC, 190, "mmHg", 1000);
        feedVital(VitalType.BP_SYSTOLIC, 170, "mmHg", 2000);
        feedVital(VitalType.BP_SYSTOLIC, 200, "mmHg", 3000);
        feedVital(VitalType.HR, 72, "bpm", 4000);
        feedVital(VitalType.HR, 75, "bpm", 5000);
        eventRunner.tick(6000);

        assertThat(capturedEvents)
            .filteredOn(e -> e.category() == ClinicalCategory.HYPERTENSION)
            .hasSize(2)
            .allMatch(e -> e.severity() == Severity.CRITICAL)
            .allMatch(e -> e.rationale().contains("Systolic > 180"));
    }

    @Test
    void clinicalClassification_defaultVitalTypes_produceNoEvents() {
        feedVital(VitalType.RR, 18, "breaths/min", 1000);
        feedVital(VitalType.TEMP, 37.0, "C", 2000);
        feedVital(VitalType.BP_DIASTOLIC, 80, "mmHg", 3000);
        feedVital(VitalType.RR, 20, "breaths/min", 4000);
        feedVital(VitalType.TEMP, 36.8, "C", 5000);
        eventRunner.tick(6000);

        assertThat(capturedEvents).isEmpty();
    }

    // --- Branch coverage: CarePhaseSummariser ---

    @Test
    void phaseClassification_earlyWarning_singleCriticalEvent() {
        feedVital(VitalType.HR, 135, "bpm", 1000);
        feedVital(VitalType.HR, 72, "bpm", 2000);
        feedVital(VitalType.HR, 75, "bpm", 3000);
        feedVital(VitalType.SPO2, 96, "%", 4000);
        feedVital(VitalType.HR, 78, "bpm", 5000);
        eventRunner.tick(6000);

        phaseRunner.tick(1_000_000);

        assertThat(capturedPhases).hasSize(1);
        assertThat(capturedPhases.get(0).phase()).isEqualTo("EARLY_WARNING");
        assertThat(capturedPhases.get(0).rationale()).contains("Single critical event");
    }

    @Test
    void phaseClassification_emptyBatch_noPhaseProduced() {
        feedVital(VitalType.HR, 72, "bpm", 1000);
        feedVital(VitalType.HR, 75, "bpm", 2000);
        feedVital(VitalType.HR, 78, "bpm", 3000);
        feedVital(VitalType.HR, 74, "bpm", 4000);
        feedVital(VitalType.HR, 76, "bpm", 5000);
        eventRunner.tick(6000);

        assertThat(capturedEvents).as("all normal HRs produce no clinical events").isEmpty();
        phaseRunner.tick(1_000_000);
        assertThat(capturedPhases).as("no events → no phase").isEmpty();
    }

    private void feedVital(VitalType type, double value, String unit, long timestamp) {
        vitalsBus.publish(new LevelEvent<>(
            new VitalReading(type, value, unit), timestamp, L1_VITALS));
    }
}
