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
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.blocks.prompt.SystemPromptCustomiser;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class LlmAgentRoutingStrategy implements AgentRoutingStrategy {

    private static final System.Logger LOG =
            System.getLogger(LlmAgentRoutingStrategy.class.getName());

    private final @Nullable AgentProvider              agentProvider;
    private final @Nullable TrustCandidateClassifier   classifier;
    private final @Nullable TrustScoreSource           scoreSource;
    private final @Nullable TrustRoutingPolicyProvider policyProvider;
    private final           RoutingPromptAssembler     promptAssembler;
    private final @Nullable SystemPromptCustomiser     systemPromptCustomiser;
    private final int promptBudgetChars;

    public LlmAgentRoutingStrategy(
            final @Nullable AgentProvider agentProvider,
            final @Nullable TrustCandidateClassifier classifier,
            final @Nullable TrustScoreSource scoreSource,
            final @Nullable TrustRoutingPolicyProvider policyProvider,
            final RoutingPromptAssembler promptAssembler,
            final @Nullable SystemPromptCustomiser systemPromptCustomiser,
            final int promptBudgetChars) {
        this.agentProvider          = agentProvider;
        this.classifier             = classifier;
        this.scoreSource            = scoreSource;
        this.policyProvider         = policyProvider;
        this.promptAssembler        = promptAssembler;
        this.systemPromptCustomiser = systemPromptCustomiser;
        this.promptBudgetChars      = promptBudgetChars;
    }

    public LlmAgentRoutingStrategy(
            final @Nullable AgentProvider agentProvider,
            final @Nullable TrustCandidateClassifier classifier,
            final @Nullable TrustScoreSource scoreSource,
            final @Nullable TrustRoutingPolicyProvider policyProvider,
            final RoutingPromptAssembler promptAssembler,
            final @Nullable SystemPromptCustomiser systemPromptCustomiser) {
        this(agentProvider, classifier, scoreSource, policyProvider,
             promptAssembler, systemPromptCustomiser, Integer.MAX_VALUE);
    }

    @Override
    public String id() {
        return "llm";
    }

    @Override
    public RoutingResult select(
            final AgentRoutingContext context, final List<AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
            return RoutingResult.unresolvable("no candidates available");
        }
        if (agentProvider == null) {
            return RoutingResult.unresolvable("AgentProvider not available");
        }

        return doSelect(context, candidates);
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

        final String caseContextSummary = context.caseContext() != null
                                          && !context.caseContext().isNull()
                                          ? context.caseContext().toString()
                                          : null;
        String prompt =
                RoutingSupport.buildUserPrompt(context.capabilityName(), caseContextSummary, eligible);

        final String enrichment = promptAssembler.assemble(context, eligible, promptBudgetChars);
        if (enrichment != null) {
            prompt = prompt + "\n\n" + enrichment;
        }

        final String systemPrompt = systemPromptCustomiser != null
                                    ? systemPromptCustomiser.customise(RoutingSupport.SYSTEM_PROMPT, "llm-routing", "control")
                                    : RoutingSupport.SYSTEM_PROMPT;
        final String response =
                RoutingSupport.invokeAndCollect(agentProvider, systemPrompt, prompt);

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
