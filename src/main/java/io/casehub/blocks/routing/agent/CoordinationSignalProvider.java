package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
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
public class CoordinationSignalProvider implements RoutingSignalProvider {

  private final CoordinationOutcomeWeights outcomeWeights;

  @Inject
  public CoordinationSignalProvider(CoordinationOutcomeWeights outcomeWeights) {
    this.outcomeWeights = outcomeWeights;
  }

  @Override
  public String id() {
    return "coordination";
  }

  @Override
  public @Nullable RoutingSignal evaluate(
      AgentRoutingContext context, List<AgentCandidate> eligible) {
    List<RetrievedExperience> experiences = context.experiences();
    if (experiences == null || experiences.isEmpty()) {
      return null;
    }

    Set<String> eligibleIds =
        eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());
    Map<String, Double> weights = outcomeWeights.weights();
    Map<String, double[]> workerStats = new HashMap<>();

    for (var exp : experiences) {
      Set<String> team = extractTeam(exp.planTrace());
      if (team.size() < 2) {
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

      long overlapCount = team.stream().filter(eligibleIds::contains).count();
      double teamOverlap = (double) overlapCount / team.size();
      if (teamOverlap == 0.0) {
        continue;
      }

      double adjustedRelevance = relevance * teamOverlap;

      for (String workerId : team) {
        if (eligibleIds.contains(workerId)) {
          var stats = workerStats.computeIfAbsent(workerId, k -> new double[]{0.0, 0.0});
          stats[0] += caseWeight * adjustedRelevance;
          stats[1] += adjustedRelevance;
        }
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
        candidates.put(
            entry.getKey(),
            new RoutingSignal.CandidateSignal.Score(score, "coordination team affinity"));
      }
    }

    return candidates.isEmpty() ? null : new RoutingSignal(candidates);
  }

  static Set<String> extractTeam(List<ExperiencePlanStep> planTrace) {
    return planTrace.stream()
        .map(ExperiencePlanStep::workerName)
        .filter(name -> name != null)
        .collect(Collectors.toSet());
  }
}
