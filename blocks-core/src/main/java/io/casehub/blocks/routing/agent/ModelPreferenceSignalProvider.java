package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ModelPreferenceSignalProvider implements RoutingSignalProvider {

    static final String MODEL_ID_KEY = "modelId";

    private final CbrOutcomeWeights outcomeWeights;

    @Inject
    public ModelPreferenceSignalProvider(CbrOutcomeWeights outcomeWeights) {
        this.outcomeWeights = outcomeWeights;
    }

    @Override
    public String id() {
        return "model-preference";
    }

    @Override
    public @Nullable RoutingSignal evaluate(AgentRoutingContext context,
                                             List<AgentCandidate> eligible) {
        List<RetrievedExperience> experiences = context.experiences();
        if (experiences == null || experiences.isEmpty()) {
            return null;
        }

        Set<String> eligibleIds =
                eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());
        Map<RoutingOutcome, Double> weights = outcomeWeights.weights();

        // (workerId, modelId) → [weightedSum, similaritySum]
        Map<String, Map<String, double[]>> workerModelStats = new HashMap<>();

        for (var exp : experiences) {
            double similarity = Math.max(0.0, exp.similarityScore());
            if (similarity == 0.0) continue;

            for (var step : exp.planTrace()) {
                if (!context.capabilityName().equals(step.capabilityName())
                        || step.workerName() == null
                        || !eligibleIds.contains(step.workerName())) {
                    continue;
                }

                Object modelIdObj = step.parameters().get(MODEL_ID_KEY);
                if (!(modelIdObj instanceof String modelId)) continue;

                double outcomeWeight = weights.getOrDefault(step.stepOutcome(), 0.0);
                workerModelStats
                        .computeIfAbsent(step.workerName(), k -> new HashMap<>())
                        .computeIfAbsent(modelId, k -> new double[]{0.0, 0.0});
                var stats = workerModelStats.get(step.workerName()).get(modelId);
                stats[0] += outcomeWeight * similarity;
                stats[1] += similarity;
            }
        }

        if (workerModelStats.isEmpty()) return null;

        Map<String, RoutingSignal.CandidateSignal> candidates = new HashMap<>();
        for (var workerEntry : workerModelStats.entrySet()) {
            String bestModel = null;
            double bestScore = -1.0;

            for (var modelEntry : workerEntry.getValue().entrySet()) {
                double[] stats = modelEntry.getValue();
                if (stats[1] > 0.0) {
                    double score = stats[0] / stats[1];
                    if (score > bestScore) {
                        bestScore = score;
                        bestModel = modelEntry.getKey();
                    }
                }
            }

            if (bestModel != null && bestScore > 0.0) {
                candidates.put(workerEntry.getKey(),
                        new RoutingSignal.CandidateSignal.Score(bestScore,
                                "model-preference:" + bestModel));
            }
        }

        return candidates.isEmpty() ? null : new RoutingSignal(candidates);
    }
}
