package io.casehub.blocks.summarisation.examples.clinical;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClinicalEventSummariser implements Summariser.SyncSummariser<VitalReading, ClinicalEvent> {

    @Override
    public List<ClinicalEvent> summarise(List<LevelEvent<VitalReading>> batch) {
        var events = new ArrayList<ClinicalEvent>();
        for (var e : batch) {
            var reading = e.payload();
            classify(reading).ifPresent(events::add);
        }
        return events;
    }

    private Optional<ClinicalEvent> classify(VitalReading reading) {
        return switch (reading.type()) {
            case HR -> {
                if (reading.value() > 130) yield Optional.of(new ClinicalEvent(
                    ClinicalCategory.TACHYCARDIA, Severity.CRITICAL,
                    "HR " + reading.value() + " " + reading.unit(),
                    "HR > 130 bpm"));
                if (reading.value() > 100) yield Optional.of(new ClinicalEvent(
                    ClinicalCategory.TACHYCARDIA, Severity.MODERATE,
                    "HR " + reading.value() + " " + reading.unit(),
                    "HR > 100 bpm"));
                if (reading.value() < 50) yield Optional.of(new ClinicalEvent(
                    ClinicalCategory.BRADYCARDIA, Severity.MODERATE,
                    "HR " + reading.value() + " " + reading.unit(),
                    "HR < 50 bpm"));
                yield Optional.empty();
            }
            case SPO2 -> {
                if (reading.value() < 90) yield Optional.of(new ClinicalEvent(
                    ClinicalCategory.HYPOXEMIA, Severity.CRITICAL,
                    "SpO2 " + reading.value() + "%",
                    "SpO2 < 90%"));
                if (reading.value() < 94) yield Optional.of(new ClinicalEvent(
                    ClinicalCategory.HYPOXEMIA, Severity.MODERATE,
                    "SpO2 " + reading.value() + "%",
                    "SpO2 < 94%"));
                yield Optional.empty();
            }
            case BP_SYSTOLIC -> {
                if (reading.value() > 180) yield Optional.of(new ClinicalEvent(
                    ClinicalCategory.HYPERTENSION, Severity.CRITICAL,
                    "BP " + reading.value() + " " + reading.unit(),
                    "Systolic > 180 mmHg"));
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }
}
