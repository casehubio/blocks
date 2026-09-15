package io.casehub.blocks.summarisation.narrative;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionSignalTest {

    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void routingDecision_validatesRequiredFields() {
        assertThatThrownBy(() -> new RoutingDecision(null, "step", NOW, "agent", "cbr", 0.5, List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void routingDecision_validatesScoreRange() {
        assertThatThrownBy(() -> new RoutingDecision("case1", "step", NOW, "agent", "cbr", 1.5, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void routingDecision_defensiveCopiesCandidateIds() {
        var ids = new java.util.ArrayList<>(List.of("a", "b"));
        var rd = new RoutingDecision("case1", "step", NOW, "agent", "cbr", 0.5, ids, null);
        ids.add("c");
        assertThat(rd.candidateIds()).hasSize(2);
    }

    @Test
    void cbrRetrieval_validatesCountRange() {
        assertThatThrownBy(() -> new CbrRetrieval("case1", "step", NOW, -1, 0.5, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cbrRetrieval_validatesSimilarityRange() {
        assertThatThrownBy(() -> new CbrRetrieval("case1", "step", NOW, 5, 1.5, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trustAssessment_validatesScoreRange() {
        assertThatThrownBy(() -> new TrustAssessment("case1", "step", NOW, "agent", -0.1, 0.5, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deliberationOutcome_validatesRoundsNonNegative() {
        assertThatThrownBy(() -> new DeliberationOutcome("case1", "step", NOW, "AGREED", -1, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deliberationOutcome_defensiveCopiesParticipants() {
        var parts = new java.util.ArrayList<>(List.of("p1", "p2"));
        var d = new DeliberationOutcome("case1", "step", NOW, "AGREED", 3, "CONSENSUS", parts);
        parts.add("p3");
        assertThat(d.participantIds()).hasSize(2);
    }

    @Test
    void stepOutcome_validatesRequiredFields() {
        assertThatThrownBy(() -> new StepOutcome("case1", "step", NOW, null, null, null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void signalDigest_validatesConfidenceRange() {
        assertThatThrownBy(() -> new SignalDigest("type", "summary", Map.of(), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signalDigest_defensiveCopiesKeyFacts() {
        var facts = new java.util.HashMap<>(Map.of("k", "v"));
        var sd = new SignalDigest("type", "summary", facts, 0.5);
        facts.put("k2", "v2");
        assertThat(sd.keyFacts()).hasSize(1);
    }

    @Test
    void stepDecisionSummary_validatesRequiredFields() {
        assertThatThrownBy(() -> new StepDecisionSummary(null, "step", List.of(), NOW, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void stepDecisionSummary_defensiveCopiesSignals() {
        var signals = new java.util.ArrayList<>(List.of(
                new SignalDigest("type", "summary", Map.of(), 0.5)));
        var sds = new StepDecisionSummary("case1", "step", signals, NOW, NOW);
        signals.add(new SignalDigest("type2", "sum2", Map.of(), 0.9));
        assertThat(sds.signals()).hasSize(1);
    }

    @Test
    void decisionNarrative_validatesConfidenceRange() {
        assertThatThrownBy(() -> new DecisionNarrative("case1", List.of("s1"), "text", List.of(), 1.5, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisionNarrative_defensiveCopies() {
        var steps = new java.util.ArrayList<>(List.of("s1"));
        var sources = new java.util.ArrayList<>(List.of("src1"));
        var dn = new DecisionNarrative("case1", steps, "explanation", sources, 0.8, NOW);
        steps.add("s2");
        sources.add("src2");
        assertThat(dn.stepNames()).hasSize(1);
        assertThat(dn.evidenceSources()).hasSize(1);
    }

    @Test
    void decisionSignal_sealedHierarchyExhaustive() {
        DecisionSignal signal = new StepOutcome("c1", "s1", NOW, "COMPLETED", "w1", null, Duration.ofSeconds(1));
        var result = switch (signal) {
            case RoutingDecision r -> "routing";
            case CbrRetrieval c -> "cbr";
            case TrustAssessment t -> "trust";
            case DeliberationOutcome d -> "deliberation";
            case StepOutcome s -> "step";
            case ModelSelection m -> "model";
        };
        assertThat(result).isEqualTo("step");
    }
}
