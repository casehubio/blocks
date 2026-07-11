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
import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * {@link AgentRoutingStrategy} that uses LLM reasoning to select agents from a candidate pool,
 * with optional trust-based classification and filtering.
 *
 * <h3>Operation modes</h3>
 *
 * <ul>
 *   <li><b>Pure LLM mode</b>: when trust services are unavailable, delegates selection entirely to
 *       the LLM. Never escalates to oversight.
 *   <li><b>Trust-filtered LLM mode</b>: when {@link TrustCandidateClassifier}, {@link
 *       TrustScoreSource}, and {@link TrustRoutingPolicyProvider} are available, applies
 *       trust-based pre-screening before LLM selection.
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
 *   <li>Otherwise, invoke LLM with the filtered pool.
 * </ol>
 *
 * <h3>LLM invocation</h3>
 *
 * Delegates to {@link RoutingSupport} for prompt construction, LLM invocation, and response
 * parsing. Returns {@link AgentAssignment.Unresolvable} on LLM failure, unparseable response, or
 * unknown agent selection.
 *
 * <p>All blocking work runs on {@code Infrastructure.getDefaultWorkerPool()} via {@code
 * Uni.emitOn()}.
 */
@ApplicationScoped
public class LlmAgentRoutingStrategy implements AgentRoutingStrategy {

  private static final System.Logger LOG =
      System.getLogger(LlmAgentRoutingStrategy.class.getName());

  private final @Nullable AgentProvider agentProvider;
  private final @Nullable TrustCandidateClassifier classifier;
  private final @Nullable TrustScoreSource scoreSource;
  private final @Nullable TrustRoutingPolicyProvider policyProvider;
  private final RoutingPromptAssembler promptAssembler;

  @Inject
  public LlmAgentRoutingStrategy(
      final Instance<AgentProvider> agentProvider,
      final Instance<TrustCandidateClassifier> classifier,
      final Instance<TrustScoreSource> scoreSource,
      final Instance<TrustRoutingPolicyProvider> policyProvider,
      final RoutingPromptAssembler promptAssembler) {
    this.agentProvider = agentProvider.isUnsatisfied() ? null : agentProvider.get();
    this.classifier = classifier.isUnsatisfied() ? null : classifier.get();
    this.scoreSource = scoreSource.isUnsatisfied() ? null : scoreSource.get();
    this.policyProvider = policyProvider.isUnsatisfied() ? null : policyProvider.get();
    this.promptAssembler = promptAssembler;
  }

  @Override
  public String id() {
    return "llm";
  }

    @Override
    public Uni<RoutingResult> select(
            final AgentRoutingContext context, final List<AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Uni.createFrom().item(RoutingResult.unresolvable("no candidates available"));
        }
        if (agentProvider == null) {
            return Uni.createFrom()
                      .item(RoutingResult.unresolvable("AgentProvider not available"));
        }

        return Uni.createFrom()
                  .item(() -> doSelect(context, candidates))
                  .emitOn(Infrastructure.getDefaultWorkerPool());
    }

  private RoutingResult doSelect(
          final AgentRoutingContext context, final List<AgentCandidate> candidates) {
    final var trustOutcome = RoutingSupport.applyTrustFilter(
            classifier, scoreSource, policyProvider, context, candidates);

    if (trustOutcome instanceof RoutingSupport.TrustFilterOutcome.Decided decided) {
      return decided.assignment();
    }

    final var proceed  = (RoutingSupport.TrustFilterOutcome.Proceed) trustOutcome;
    final var eligible = proceed.eligible();

    // NullNode case context filtering — avoid sending "null" as context to the LLM
    final String caseContextSummary = context.caseContext() != null
                                      && !context.caseContext().isNull()
                                      ? context.caseContext().toString()
                                      : null;
    String prompt =
            RoutingSupport.buildUserPrompt(context.capabilityName(), caseContextSummary, eligible);

    final String enrichment = promptAssembler.assemble(context, eligible);
    if (enrichment != null) {
      prompt = prompt + "\n\n" + enrichment;
    }

    final String response =
            RoutingSupport.invokeAndCollect(agentProvider, RoutingSupport.SYSTEM_PROMPT, prompt);

    if (response == null) {
      if (proceed.classified() != null) {
        final var scored = proceed.classified().stream()
                                  .map(c -> new ScoredCandidate(c, 0.0, "LLM invocation failed"))
                                  .toList();
        return classifier.decide(proceed.classified(), scored, context.capabilityName());
      }
      return RoutingResult.unresolvable("LLM invocation failed or returned no response");
    }

    final String workerId = RoutingSupport.parseSelection(response, eligible);
    if (workerId == null) {
      if (proceed.classified() != null) {
        final var scored = proceed.classified().stream()
                                  .map(c -> new ScoredCandidate(c, 0.0, "LLM response unparseable"))
                                  .toList();
        return classifier.decide(proceed.classified(), scored, context.capabilityName());
      }
      return RoutingResult.unresolvable(
              "LLM response unparseable or selected unknown agent: " + response);
    }

    return RoutingResult.assigned(
            workerId, "LLM selected from %d candidates".formatted(eligible.size()));
  }
}
