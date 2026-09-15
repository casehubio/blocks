package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionSignalSummariserTest {

    private static final Instant T1 = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-11T10:00:01Z");
    private static final EventLevel INPUT = new EventLevel("input", 0);

    private final DecisionSignalSummariser summariser = new DecisionSignalSummariser();

    @Test
    void emptyBatch_returnsEmpty() {
        assertThat(summariser.summarise(List.of())).isEmpty();
    }

    @Test
    void stepOutcome_producesDigestWithCorrectFields() {
        var signal = new StepOutcome("case1", "route-analyst", T1, "COMPLETED", "worker1", null, Duration.ofSeconds(2));
        var batch = List.of(new LevelEvent<DecisionSignal>(signal, T1.toEpochMilli(), INPUT, null));

        var result = summariser.summarise(batch);

        assertThat(result).hasSize(1);
        var summary = result.get(0);
        assertThat(summary.caseId()).isEqualTo("case1");
        assertThat(summary.stepName()).isEqualTo("route-analyst");
        assertThat(summary.signals()).hasSize(1);

        var digest = summary.signals().get(0);
        assertThat(digest.signalType()).isEqualTo("StepOutcome");
        assertThat(digest.keyFacts()).containsEntry("status", "COMPLETED");
        assertThat(digest.keyFacts()).containsEntry("worker", "worker1");
        assertThat(digest.confidence()).isEqualTo(1.0);
    }

    @Test
    void routingDecision_producesDigestWithScore() {
        var signal = new RoutingDecision("case1", "route", T1, "agent-3", "cbr", 0.87, List.of("agent-1", "agent-2", "agent-3"), null);
        var batch = List.of(new LevelEvent<DecisionSignal>(signal, T1.toEpochMilli(), INPUT, null));

        var result = summariser.summarise(batch);

        var digest = result.get(0).signals().get(0);
        assertThat(digest.signalType()).isEqualTo("RoutingDecision");
        assertThat(digest.keyFacts()).containsEntry("selected", "agent-3");
        assertThat(digest.keyFacts()).containsEntry("candidates", "3");
        assertThat(digest.confidence()).isEqualTo(0.87);
    }

    @Test
    void multipleSignals_singleSummaryWithAllDigests() {
        var routing = new RoutingDecision("case1", "step1", T1, "agent-3", "cbr", 0.87, List.of("a1", "a2", "a3"), null);
        var trust = new TrustAssessment("case1", "step1", T2, "agent-3", 0.85, 0.70, true);

        var batch = List.of(
                new LevelEvent<DecisionSignal>(routing, T1.toEpochMilli(), INPUT, null),
                new LevelEvent<DecisionSignal>(trust, T2.toEpochMilli(), INPUT, null));

        var result = summariser.summarise(batch);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signals()).hasSize(2);
        assertThat(result.get(0).from()).isEqualTo(T1);
        assertThat(result.get(0).to()).isEqualTo(T2);
    }

    @Test
    void allSixSignalTypes_exhaustiveSwitch() {
        List<DecisionSignal> signals = List.of(
                new RoutingDecision("c", "s", T1, "a", "strat", 0.5, List.of(), null),
                new CbrRetrieval("c", "s", T1, 3, 0.8, "COMPLETED", null),
                new TrustAssessment("c", "s", T1, "a", 0.9, 0.7, true),
                new DeliberationOutcome("c", "s", T1, "AGREED", 2, "CONSENSUS", List.of("p1")),
                new StepOutcome("c", "s", T1, "COMPLETED", "w", null, Duration.ofSeconds(1)),
                new ModelSelection("c", "s", T1, "claude-opus-4", "flagship", "sentiment-analysis", "Anthropic", "Claude Opus 4"));

        var batch = signals.stream()
                .map(s -> new LevelEvent<DecisionSignal>(s, T1.toEpochMilli(), INPUT, null))
                .toList();

        var result = summariser.summarise(batch);
        assertThat(result.get(0).signals()).hasSize(6);
    }

    @Test
    void modelSelection_producesDigestWithModelDetails() {
        var signal = new ModelSelection("case1", "analyse", T1, "claude-opus-4", "flagship", "sentiment-analysis", "Anthropic", "Claude Opus 4");
        var batch = List.of(new LevelEvent<DecisionSignal>(signal, T1.toEpochMilli(), INPUT, null));

        var result = summariser.summarise(batch);

        var digest = result.get(0).signals().get(0);
        assertThat(digest.signalType()).isEqualTo("ModelSelection");
        assertThat(digest.keyFacts()).containsEntry("model", "claude-opus-4");
        assertThat(digest.keyFacts()).containsEntry("tier", "flagship");
        assertThat(digest.keyFacts()).containsEntry("capability", "sentiment-analysis");
        assertThat(digest.confidence()).isEqualTo(1.0);
    }
}
