package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RoutingSignalProvider} that scores candidates based on immediate predecessor context
 * in historical plan traces.
 *
 * <p>For each retrieved experience with {@code planTrace.size() >= 2}, this analyser sorts steps
 * by priority, finds steps matching the target capability, and scores each eligible candidate
 * based on the case outcome weighted by similarity — recording the immediate predecessor's
 * (capability, worker) pair for observability.
 */
@ApplicationScoped
public class PredecessorAnalyser implements RoutingSignalProvider {

    private final CbrCaseOutcomeWeights caseOutcomeWeights;

    @Inject
    public PredecessorAnalyser(Instance<CbrCaseOutcomeWeights> caseOutcomeWeights) {
        this.caseOutcomeWeights = caseOutcomeWeights.get();
    }

    PredecessorAnalyser(CbrCaseOutcomeWeights caseOutcomeWeights) {
        this.caseOutcomeWeights = caseOutcomeWeights;
    }

    @Override
    public String id() {
        return "predecessor";
    }

    @Override
    public @Nullable RoutingSignal signal(AgentRoutingContext context, List<AgentCandidate> eligible) {
        List<RetrievedExperience> experiences = context.experiences();
        if (experiences == null || experiences.isEmpty()) {
            return null;
        }

        Set<String> eligibleIds =
                eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());
        Map<String, Double> weights = caseOutcomeWeights.weights();
        Map<String, double[]> workerStats = new HashMap<>();
        Map<String, String> workerPredecessors = new HashMap<>();

        for (var exp : experiences) {
            if (exp.planTrace().size() < 2) {
                continue;
            }
            double relevance = Math.max(0.0, exp.similarityScore());
            if (relevance == 0.0) {
                continue;
            }
            double caseWeight = weights.getOrDefault(exp.outcome(), 0.0);
            if (caseWeight == 0.0) {
                continue;
            }

            List<ExperiencePlanStep> sorted = exp.planTrace().stream()
                    .sorted(Comparator.comparingInt(ExperiencePlanStep::priority))
                    .toList();

            for (int i = 1; i < sorted.size(); i++) {
                ExperiencePlanStep step = sorted.get(i);
                if (!context.capabilityName().equals(step.capabilityName())
                        || step.workerName() == null
                        || !eligibleIds.contains(step.workerName())) {
                    continue;
                }

                ExperiencePlanStep predecessor = sorted.get(i - 1);
                var stats = workerStats.computeIfAbsent(step.workerName(), k -> new double[]{0.0, 0.0});
                stats[0] += caseWeight * relevance;
                stats[1] += relevance;

                String predKey = predecessor.capabilityName() + ":" + predecessor.workerName();
                workerPredecessors.putIfAbsent(step.workerName(), predKey);
            }
        }

        if (workerStats.isEmpty()) {
            return null;
        }

        Map<String, RoutingSignal.CandidateSignal> candidates = new HashMap<>();
        for (var entry : workerStats.entrySet()) {
            double evidenceMass = entry.getValue()[1];
            if (evidenceMass > 0.0) {
                double score = entry.getValue()[0] / evidenceMass;
                String pred = workerPredecessors.getOrDefault(entry.getKey(), "unknown");
                candidates.put(entry.getKey(),
                        new RoutingSignal.CandidateSignal(score,
                                "predecessor analysis (after " + pred + ")"));
            }
        }

        return candidates.isEmpty() ? null : new RoutingSignal(candidates);
    }
}
