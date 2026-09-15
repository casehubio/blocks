package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.List;
import java.util.Map;

public class DecisionSignalSummariser
        implements Summariser.SyncSummariser<DecisionSignal, StepDecisionSummary> {

    @Override
    public List<StepDecisionSummary> summarise(List<LevelEvent<DecisionSignal>> batch) {
        if (batch.isEmpty()) return List.of();

        var first = batch.get(0).payload();
        var digests = batch.stream()
                .map(e -> toDigest(e.payload()))
                .toList();

        return List.of(new StepDecisionSummary(
                first.caseId(),
                first.stepName(),
                digests,
                batch.get(0).payload().timestamp(),
                batch.get(batch.size() - 1).payload().timestamp()));
    }

    private static SignalDigest toDigest(DecisionSignal signal) {
        return switch (signal) {
            case RoutingDecision r -> new SignalDigest(
                    "RoutingDecision",
                    "Selected " + r.selectedAgentId() + " via " + r.strategyId(),
                    Map.of("selected", r.selectedAgentId(),
                           "strategy", r.strategyId(),
                           "score", String.valueOf(r.score()),
                           "candidates", String.valueOf(r.candidateIds().size())),
                    r.score());
            case CbrRetrieval c -> new SignalDigest(
                    "CbrRetrieval",
                    "Retrieved " + c.retrievedCount() + " cases, top similarity " + c.topSimilarity(),
                    Map.of("retrieved", String.valueOf(c.retrievedCount()),
                           "topSimilarity", String.valueOf(c.topSimilarity()),
                           "topOutcome", c.topCaseOutcome() != null ? c.topCaseOutcome() : "unknown"),
                    c.topSimilarity());
            case TrustAssessment t -> new SignalDigest(
                    "TrustAssessment",
                    t.agentId() + " trust " + t.trustScore() + (t.passed() ? " (passed)" : " (failed)"),
                    Map.of("agent", t.agentId(),
                           "trust", String.valueOf(t.trustScore()),
                           "threshold", String.valueOf(t.threshold()),
                           "passed", String.valueOf(t.passed())),
                    t.trustScore());
            case DeliberationOutcome d -> new SignalDigest(
                    "DeliberationOutcome",
                    d.outcome() + " after " + d.rounds() + " rounds",
                    Map.of("outcome", d.outcome(),
                           "rounds", String.valueOf(d.rounds()),
                           "convergence", d.convergenceState() != null ? d.convergenceState() : "unknown"),
                    d.convergenceState() != null && d.convergenceState().equals("CONSENSUS") ? 1.0 : 0.5);
            case StepOutcome s -> new SignalDigest(
                    "StepOutcome",
                    s.stepName() + " " + s.status() + " in " + s.elapsed(),
                    Map.of("status", s.status(),
                           "worker", s.workerId() != null ? s.workerId() : "unknown",
                           "elapsed", s.elapsed().toString()),
                    s.status().equals("COMPLETED") ? 1.0 : 0.3);
            case ModelSelection m -> new SignalDigest(
                    "ModelSelection",
                    m.displayName() + " (" + m.modelTier() + " tier) for " + m.capabilityName(),
                    Map.of("model", m.modelId(),
                           "tier", m.modelTier(),
                           "capability", m.capabilityName(),
                           "vendor", m.vendor()),
                    1.0);
        };
    }
}
