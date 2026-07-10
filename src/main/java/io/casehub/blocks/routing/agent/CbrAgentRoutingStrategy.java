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

import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
 * <h3>Operation modes</h3>
 *
 * <ul>
 *   <li><b>CBR mode</b>: when {@link CbrCaseMemoryStore} is available, retrieves similar past
 *       cases and analyses worker success rates from plan traces.
 *   <li><b>Graph fallback mode</b>: when CBR produces no match but {@link AgentGraphQuery} is
 *       available, delegates to graph-based outcome ranking.
 *   <li><b>Trust-filtered mode</b>: when {@link TrustCandidateClassifier}, {@link
 *       TrustScoreSource}, and {@link TrustRoutingPolicyProvider} are available, applies
 *       trust-based pre-screening before CBR analysis.
 * </ul>
 *
 * <h3>Trust classification flow</h3>
 *
 * When trust services are present, follows the four-phase trust maturity model:
 *
 * <ol>
 *   <li>Classify all candidates via {@link TrustCandidateClassifier}.
 *   <li>Apply bootstrap guard: if {@code bootstrapEscalationRequired} is {@code true} and only
 *       BOOTSTRAP candidates exist, escalate with {@link EscalationReason#NO_QUALIFIED_AGENT}.
 *   <li>Filter eligible candidates: exclude BORDERLINE, EXCLUDED_PHASE2B, and EXCLUDED_PHASE3.
 *       Also exclude BOOTSTRAP when {@code bootstrapEscalationRequired} is {@code true}.
 *   <li>If eligible pool is empty after filtering, delegate escalation decision to {@link
 *       TrustCandidateClassifier#decide}.
 *   <li>Otherwise, proceed with CBR analysis using the filtered pool.
 * </ol>
 *
 * <h3>CBR analysis</h3>
 *
 * <p>Queries {@link CbrCaseMemoryStore} with the current capability and case context. Analyses
 * retrieved cases by type:
 *
 * <ul>
 *   <li><b>PlanCbrCase</b>: extracts worker success rates from {@link PlanTrace} entries matching
 *       the current capability. Selects the worker with the highest success rate (ties broken by
 *       total observation count).
 *   <li><b>TextualCbrCase / FeatureVectorCbrCase</b>: lack worker identity, produce no match.
 * </ul>
 *
 * <h3>Fallback chain</h3>
 *
 * <p>If CBR produces no match:
 *
 * <ol>
 *   <li>Try {@link AgentGraphQuery#topAgentsByOutcome} if available — returns first graph-ranked
 *       agent that appears in the eligible pool.
 *   <li>If trust classified and graph also failed, delegate to {@link
 *       TrustCandidateClassifier#decide}.
 *   <li>Otherwise, return {@link AgentAssignment#unresolvable}.
 * </ol>
 *
 * <p>All blocking work runs on {@code Infrastructure.getDefaultWorkerPool()} via {@code
 * Uni.emitOn()}.
 */
@ApplicationScoped
public class CbrAgentRoutingStrategy implements AgentRoutingStrategy {

  private static final System.Logger LOG =
      System.getLogger(CbrAgentRoutingStrategy.class.getName());

  private static final Map<String, Double> OUTCOME_WEIGHTS = Map.of(
      "SUCCESS", 1.0,
      "GATE_EXPIRED", 0.5,
      "GATE_REJECTED", 0.25,
      "FAILURE", 0.0
  );

  private final int topK;
  private final double minSimilarity;
  private final @Nullable CbrCaseMemoryStore cbrStore;
  private final @Nullable AgentGraphQuery graphQuery;
  private final @Nullable TrustCandidateClassifier classifier;
  private final @Nullable TrustScoreSource scoreSource;
  private final @Nullable TrustRoutingPolicyProvider policyProvider;
  private final RoutingFeatureExtractor featureExtractor;

  @Inject
  public CbrAgentRoutingStrategy(
      @ConfigProperty(name = "casehub.blocks.cbr.top-k", defaultValue = "10") final int topK,
      @ConfigProperty(name = "casehub.blocks.cbr.min-similarity",
                      defaultValue = "0.5") final double minSimilarity,
      final Instance<CbrCaseMemoryStore> cbrStore,
      final Instance<AgentGraphQuery> graphQuery,
      final Instance<TrustCandidateClassifier> classifier,
      final Instance<TrustScoreSource> scoreSource,
      final Instance<TrustRoutingPolicyProvider> policyProvider,
      final RoutingFeatureExtractor featureExtractor) {
    this.topK = topK;
    this.minSimilarity = minSimilarity;
    this.cbrStore = cbrStore.isUnsatisfied() ? null : cbrStore.get();
    this.graphQuery = graphQuery.isUnsatisfied() ? null : graphQuery.get();
    this.classifier = classifier.isUnsatisfied() ? null : classifier.get();
    this.scoreSource = scoreSource.isUnsatisfied() ? null : scoreSource.get();
    this.policyProvider = policyProvider.isUnsatisfied() ? null : policyProvider.get();
    this.featureExtractor = featureExtractor;
  }

  @Override
  public String id() {
    return "cbr";
  }

  @Override
  public Uni<AgentAssignment> select(
      final AgentRoutingContext context, final List<AgentCandidate> candidates) {
    if (candidates.isEmpty()) {
      return Uni.createFrom().item(AgentAssignment.unresolvable("no candidates available"));
    }
    if (cbrStore == null && graphQuery == null) {
      return Uni.createFrom()
          .item(AgentAssignment.unresolvable("CBR and graph query both unavailable"));
    }

    return Uni.createFrom()
        .item(() -> doSelect(context, candidates))
        .emitOn(Infrastructure.getDefaultWorkerPool());
  }

  private AgentAssignment doSelect(
      final AgentRoutingContext context, final List<AgentCandidate> candidates) {
    final var trustOutcome = RoutingSupport.applyTrustFilter(
        classifier, scoreSource, policyProvider, context, candidates);

    if (trustOutcome instanceof RoutingSupport.TrustFilterOutcome.Decided decided) {
      return decided.assignment();
    }

    final var proceed = (RoutingSupport.TrustFilterOutcome.Proceed) trustOutcome;
    final var eligible = proceed.eligible();
    final Set<String> eligibleIds =
        eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());

    // Try CBR store first
    if (cbrStore != null) {
      final String cbrResult = tryCbrStore(context, eligibleIds);
      if (cbrResult != null) {
        return AgentAssignment.assign(
            cbrResult, "CBR selected based on historical success rate");
      }
    }

    // Fallback to graph query
    if (graphQuery != null) {
      final String graphResult = tryGraphQuery(context, eligibleIds);
      if (graphResult != null) {
        return AgentAssignment.assign(graphResult, "graph query fallback — CBR produced no match");
      }
    }

    // Neither source produced a match — if trust classified, delegate decision
    if (proceed.classified() != null) {
      final List<ScoredCandidate> scored =
          proceed.classified().stream()
              .map(c -> new ScoredCandidate(c, 0.0, "no CBR or graph match"))
              .toList();
      return classifier.decide(proceed.classified(), scored, context.capabilityName());
    }

    return AgentAssignment.unresolvable("no CBR or graph match found");
  }

  private @Nullable String tryCbrStore(
      final AgentRoutingContext context, final Set<String> eligibleIds) {
    try {
      CbrQuery query =
          CbrQuery.of(
              context.tenancyId(),
              new MemoryDomain(context.capabilityName()),
              "agent-routing",
              featureExtractor.extractFeatures(context),
              topK)
              .withMinSimilarity(minSimilarity);
      final String problem = featureExtractor.extractProblem(context);
      if (problem != null) {
        query = query.withProblem(problem);
      }

      final List<ScoredCbrCase<CbrCase>> results = cbrStore.retrieveSimilar(query, CbrCase.class);
      if (results.isEmpty()) {
        return null;
      }

      return analyseByType(results, eligibleIds, context.capabilityName());
    } catch (final Exception e) {
      LOG.log(System.Logger.Level.WARNING, "CBR store query failed", e);
      return null;
    }
  }

  private @Nullable String analyseByType(
      final List<ScoredCbrCase<CbrCase>> results,
      final Set<String> eligibleIds,
      final String capabilityName) {
    final Map<String, double[]> workerStats = new HashMap<>(); // [weightedScore, total]

    for (final var scored : results) {
      final var cbrCase = scored.cbrCase();
      if (cbrCase instanceof PlanCbrCase planCase) {
        for (final PlanTrace trace : planCase.planTrace()) {
          if (capabilityName.equals(trace.capabilityName())
              && trace.workerName() != null
              && eligibleIds.contains(trace.workerName())) {
            final var stats = workerStats.computeIfAbsent(trace.workerName(), k -> new double[] {0.0, 0});
            stats[1]++;
            stats[0] += OUTCOME_WEIGHTS.getOrDefault(trace.stepOutcome(), 0.0);
          }
        }
      }
    }

    if (workerStats.isEmpty()) {
      return null;
    }

    String bestWorker = null;
    double bestRate = -1;
    int bestCount = 0;
    for (final var entry : workerStats.entrySet()) {
      final double weightedScore = entry.getValue()[0];
      final int total = (int) entry.getValue()[1];
      final double rate = weightedScore / total;
      if (rate > bestRate || (rate == bestRate && total > bestCount)) {
        bestRate = rate;
        bestCount = total;
        bestWorker = entry.getKey();
      }
    }
    return bestWorker;
  }

  private @Nullable String tryGraphQuery(
      final AgentRoutingContext context, final Set<String> eligibleIds) {
    try {
      final var ranked =
          graphQuery.topAgentsByOutcome(context.capabilityName(), null, null, topK);
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
