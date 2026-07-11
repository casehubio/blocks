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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ClassifiedCandidate;
import io.casehub.ledger.routing.TrustCandidateClassifier.Phase;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

final class RoutingSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RoutingSupport() {}

    sealed interface TrustFilterOutcome {
        record Proceed(List<AgentCandidate> eligible,
                       @Nullable List<ClassifiedCandidate> classified)
                implements TrustFilterOutcome {}
        record Decided(RoutingResult assignment)
                implements TrustFilterOutcome {}
    }

    static TrustFilterOutcome applyTrustFilter(
            @Nullable TrustCandidateClassifier classifier,
            @Nullable TrustScoreSource scoreSource,
            @Nullable TrustRoutingPolicyProvider policyProvider,
            AgentRoutingContext context,
            List<AgentCandidate> candidates) {

        if (classifier == null || scoreSource == null || policyProvider == null) {
            return new TrustFilterOutcome.Proceed(candidates, null);
        }

        TrustRoutingPolicy policy = policyProvider.forCapability(context.capabilityName());
        List<ClassifiedCandidate> classified =
                classifier.classify(candidates, context.capabilityName(), policy, scoreSource);

        if (policy.bootstrapEscalationRequired()) {
            boolean hasQualified =
                    classified.stream().anyMatch(c -> c.phase() == Phase.QUALIFIED);
            boolean hasBootstrap =
                    classified.stream().anyMatch(c -> c.phase() == Phase.BOOTSTRAP);
            if (!hasQualified && hasBootstrap) {
                return new TrustFilterOutcome.Decided(RoutingResult.escalate(
                        context.capabilityName(),
                        EscalationReason.NO_QUALIFIED_AGENT,
                        "bootstrap only — no qualified agents for capability '%s'"
                                .formatted(context.capabilityName())));
            }
        }

        List<AgentCandidate> eligible = classified.stream()
                .filter(c -> !c.isExcluded())
                .filter(c -> !policy.bootstrapEscalationRequired() || c.phase() != Phase.BOOTSTRAP)
                .map(ClassifiedCandidate::candidate)
                .toList();

        if (eligible.isEmpty()) {
            List<ScoredCandidate> scored = classified.stream()
                    .map(c -> new ScoredCandidate(c, 0.0, "excluded by trust filter"))
                    .toList();
            return new TrustFilterOutcome.Decided(
                    classifier.decide(classified, scored, context.capabilityName()));
        }

        return new TrustFilterOutcome.Proceed(eligible, classified);
    }

    static final String SYSTEM_PROMPT = """
            You are an agent router. Given a capability name, case context, and a list of \
            available agents, select the single best agent to handle the task.

            Respond with JSON only: {"agent": "<agent-name>", "reason": "<one sentence>"}

            Select based on the agent's capabilities, briefing, domain expertise, \
            and any historical evidence provided. Choose the agent whose skills \
            most closely match the task requirements, using historical evidence \
            to inform confidence when multiple agents are comparably qualified.""";

    static String buildUserPrompt(String capabilityName, @Nullable String caseContextSummary,
                                  List<AgentCandidate> candidates) {
        var sb = new StringBuilder();
        sb.append("Capability: ").append(capabilityName).append("\n\n");
        if (caseContextSummary != null && !caseContextSummary.isBlank()) {
            sb.append("Case context:\n").append(caseContextSummary).append("\n\n");
        }
        sb.append("Available agents:\n");
        for (var candidate : candidates) {
            sb.append(buildCandidateCard(candidate)).append("\n");
        }
        return sb.toString();
    }

    static String buildCandidateCard(AgentCandidate candidate) {
        var sb = new StringBuilder();
        sb.append("- Agent: \"").append(candidate.workerId()).append("\"");
        var desc = candidate.agentDescriptor();
        if (desc != null) {
            if (desc.briefing() != null && !desc.briefing().isBlank()) {
                sb.append("\n  Briefing: ").append(desc.briefing());
            }
            if (desc.capabilities() != null && !desc.capabilities().isEmpty()) {
                var caps = desc.capabilities().stream()
                        .map(AgentCapability::name)
                        .collect(Collectors.joining(", "));
                sb.append("\n  Capabilities: ").append(caps);
            }
            if (desc.slot() != null && !desc.slot().isBlank()) {
                sb.append("\n  Slot: ").append(desc.slot());
            }
        }
        sb.append("\n  Health: ").append(candidate.health());
        sb.append("\n  Running jobs: ").append(candidate.runningJobs());
        return sb.toString();
    }

    static @Nullable String parseSelection(String llmResponse, List<AgentCandidate> candidates) {
        var agentName = extractAgentName(llmResponse);
        if (agentName == null || "done".equalsIgnoreCase(agentName)) {
            return null;
        }
        for (var candidate : candidates) {
            if (candidate.workerId().equals(agentName)) {
                return candidate.workerId();
            }
            var desc = candidate.agentDescriptor();
            if (desc != null && agentName.equals(desc.name())) {
                return candidate.workerId();
            }
        }
        return null;
    }

    static @Nullable String extractAgentName(@Nullable String text) {
        if (text == null || text.isBlank()) return null;
        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        try {
            var node = MAPPER.readTree(trimmed);
            var agentNode = node.get("agent");
            return agentNode != null && agentNode.isTextual() ? agentNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static @Nullable String invokeAndCollect(AgentProvider provider,
                                             String systemPrompt, String userPrompt) {
        try {
            var config = AgentSessionConfig.of(systemPrompt, userPrompt);
            var result = provider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().indefinitely();
            return result == null || result.isBlank() ? null : result;
        } catch (Exception e) {
            return null;
        }
    }
}
