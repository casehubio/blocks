/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * {@link RoutingSignalProvider} that scores candidates based on case-level outcomes in multi-step
 * plans.
 *
 * <p>For each retrieved experience with {@code planTrace.size() >= 2}, this analyser examines
 * which eligible candidates appeared in multi-step plans and weights their contribution by the
 * case-level outcome (COMPLETED, FAULTED, CANCELLED) and the experience's similarity score.
 *
 * <p>Returns {@code null} when no multi-step plan data exists or no eligible candidates appear
 * in any trace.
 */
@ApplicationScoped
public class PlanCompositionAnalyser implements RoutingSignalProvider {

  private final CbrCaseOutcomeWeights caseOutcomeWeights;

  @Inject
  public PlanCompositionAnalyser(Instance<CbrCaseOutcomeWeights> caseOutcomeWeights) {
    this.caseOutcomeWeights = caseOutcomeWeights.get();
  }

  PlanCompositionAnalyser(CbrCaseOutcomeWeights caseOutcomeWeights) {
    this.caseOutcomeWeights = caseOutcomeWeights;
  }

  @Override
  public String id() {
    return "plan-composition";
  }

  @Override
  public @Nullable RoutingSignal signal(
      AgentRoutingContext context, List<AgentCandidate> eligible) {
    List<RetrievedExperience> experiences = context.experiences();
    if (experiences == null || experiences.isEmpty()) {
      return null;
    }

    Set<String> eligibleIds =
        eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());
    Map<String, Double> weights = caseOutcomeWeights.weights();
    Map<String, double[]> workerStats = new HashMap<>();

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

      for (var step : exp.planTrace()) {
        if (context.capabilityName().equals(step.capabilityName())
            && step.workerName() != null
            && eligibleIds.contains(step.workerName())) {
          var stats =
              workerStats.computeIfAbsent(step.workerName(), k -> new double[] {0.0, 0.0});
          stats[0] += caseWeight * relevance;
          stats[1] += relevance;
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
            new RoutingSignal.CandidateSignal(score, "plan-composition analysis"));
      }
    }

    return candidates.isEmpty() ? null : new RoutingSignal(candidates);
  }
}
