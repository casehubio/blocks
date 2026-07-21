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
import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.api.spi.routing.ExperienceAnalyser;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link AgentRoutingStrategy} that uses case-based reasoning (CBR) to select agents based on
 * historical success patterns, with optional trust-based classification and filtering.
 *
 * <p>Reads pre-retrieved experiences from {@link AgentRoutingContext#experiences()} — the engine's
 * {@code CbrRetrievalService} populates these based on the case definition's {@code CbrConfig}.
 * This strategy analyses plan traces from retrieved experiences to identify which workers have the
 * highest historical success rate for the target capability.
 *
 * <p>Falls back to {@link AgentGraphQuery} when CBR produces no match.
 */
@ApplicationScoped
public class CbrAgentRoutingStrategy implements AgentRoutingStrategy {

  private static final System.Logger LOG =
      System.getLogger(CbrAgentRoutingStrategy.class.getName());

  private final @Nullable AgentGraphQuery graphQuery;
  private final @Nullable TrustCandidateClassifier classifier;
  private final @Nullable TrustScoreSource scoreSource;
  private final @Nullable TrustRoutingPolicyProvider policyProvider;
  private final CbrOutcomeWeights outcomeWeights;
  private final @Nullable RoutingSignalAssembler signalAssembler;

  @Inject
  public CbrAgentRoutingStrategy(
      final Instance<AgentGraphQuery> graphQuery,
      final Instance<TrustCandidateClassifier> classifier,
      final Instance<TrustScoreSource> scoreSource,
      final Instance<TrustRoutingPolicyProvider> policyProvider,
      final CbrOutcomeWeights outcomeWeights,
      final Instance<RoutingSignalAssembler> signalAssembler) {
    this.graphQuery = graphQuery.isUnsatisfied() ? null : graphQuery.get();
    this.classifier = classifier.isUnsatisfied() ? null : classifier.get();
    this.scoreSource = scoreSource.isUnsatisfied() ? null : scoreSource.get();
    this.policyProvider = policyProvider.isUnsatisfied() ? null : policyProvider.get();
    this.outcomeWeights = outcomeWeights;
    this.signalAssembler = signalAssembler.isUnsatisfied() ? null : signalAssembler.get();
  }

  CbrAgentRoutingStrategy(
      @Nullable AgentGraphQuery graphQuery,
      @Nullable TrustCandidateClassifier classifier,
      @Nullable TrustScoreSource scoreSource,
      @Nullable TrustRoutingPolicyProvider policyProvider,
      CbrOutcomeWeights outcomeWeights,
      @Nullable RoutingSignalAssembler signalAssembler) {
    this.graphQuery = graphQuery;
    this.classifier = classifier;
    this.scoreSource = scoreSource;
    this.policyProvider = policyProvider;
    this.outcomeWeights = outcomeWeights;
    this.signalAssembler = signalAssembler;
  }

  @Override
  public String id() {
    return "cbr";
  }

    @Override
    public RoutingResult select(
            final AgentRoutingContext context, final List<AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
            return RoutingResult.unresolvable("no candidates available");
        }

        return doSelect(context, candidates);
    }

  private RoutingResult doSelect(
      final AgentRoutingContext context, final List<AgentCandidate> candidates) {
    final var trustOutcome =
        RoutingSupport.applyTrustFilter(
            classifier, scoreSource, policyProvider, context, candidates);

    if (trustOutcome instanceof RoutingSupport.TrustFilterOutcome.Decided decided) {
      return decided.assignment();
    }

    final var proceed = (RoutingSupport.TrustFilterOutcome.Proceed) trustOutcome;
    final var eligible = proceed.eligible();
    final Set<String> eligibleIds =
        eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());

    final Map<String, Double> experienceScores =
        analyseExperiences(context.experiences(), eligibleIds, context.capabilityName());

    final Map<String, Double> finalScores = new HashMap<>(experienceScores);
    if (signalAssembler != null) {
      final Map<String, RoutingSignal> signals = signalAssembler.assemble(context, eligible);
      for (final var signalEntry : signals.values()) {
        for (final var candidateEntry : signalEntry.candidates().entrySet()) {
          if (eligibleIds.contains(candidateEntry.getKey())) {
            finalScores.merge(
                candidateEntry.getKey(), candidateEntry.getValue().score(), Double::sum);
          }
        }
      }
    }

    String bestCandidate = null;
    double bestScore = 0.0;
    for (final var entry : finalScores.entrySet()) {
      if (entry.getValue() > bestScore) {
        bestScore = entry.getValue();
        bestCandidate = entry.getKey();
      }
    }

    if (bestCandidate != null) {
      return RoutingResult.assigned(bestCandidate, "CBR selected based on historical success rate");
    }

    if (graphQuery != null) {
      final String graphResult = tryGraphQuery(context, eligibleIds);
      if (graphResult != null) {
        return RoutingResult.assigned(
            graphResult, "graph query fallback — CBR produced no match");
      }
    }

    if (proceed.classified() != null) {
      final List<ScoredCandidate> scored =
          proceed.classified().stream()
              .map(c -> new ScoredCandidate(c, 0.0, "no CBR or graph match"))
              .toList();
      return classifier.decide(proceed.classified(), scored, context.capabilityName());
    }

    return RoutingResult.unresolvable("no CBR or graph match found");
  }

    Map<String, Double> analyseExperiences(
            final List<RetrievedExperience> experiences,
            final Set<String> eligibleIds,
            final String capabilityName) {
        return ExperienceAnalyser.workerSuccessRates(
                experiences, eligibleIds, capabilityName, outcomeWeights.weights());
    }

  private @Nullable String tryGraphQuery(
      final AgentRoutingContext context, final Set<String> eligibleIds) {
    try {
      final var ranked =
          graphQuery.topAgentsByOutcome(context.capabilityName(), null, null, 10);
      for (final var agentId : ranked) {
        if (eligibleIds.contains(agentId)) {
          return agentId;
        }
      }
      return null;
    } catch (final Exception e) {
      LOG.log(System.Logger.Level.WARNING, "AgentGraphQuery failed", e);
      return null;
    }
  }
}
