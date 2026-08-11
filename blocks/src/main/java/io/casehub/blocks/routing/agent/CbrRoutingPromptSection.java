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
import io.casehub.api.spi.routing.RoutingPromptSection;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * {@link RoutingPromptSection} that renders historical CBR evidence for LLM routing prompts.
 *
 * <p>Reads pre-retrieved experiences from {@link AgentRoutingContext#experiences()} and formats
 * agent outcome statistics and case details for the LLM to consider during routing.
 */
@ApplicationScoped
public class CbrRoutingPromptSection implements RoutingPromptSection {

  @Override
  public @Nullable String render(
      AgentRoutingContext context, List<AgentCandidate> eligible) {
    List<RetrievedExperience> experiences = context.experiences();
    if (experiences == null || experiences.isEmpty()) {
      return null;
    }

    Set<String> eligibleIds =
        eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());

    return format(experiences, eligibleIds, context.capabilityName());
  }

    private @Nullable String format(
            List<RetrievedExperience> experiences,
            Set<String> eligibleIds,
            String capabilityName) {
        Map<String, Map<String, Integer>> agentOutcomes = new LinkedHashMap<>();
        var                               caseDetails   = new StringBuilder();
        int                               caseNum       = 0;

        for (var exp : experiences) {
            for (var step : exp.planTrace()) {
                if (capabilityName.equals(step.capabilityName())
                    && step.workerName() != null
                    && eligibleIds.contains(step.workerName())) {
                    boolean isExcluded =
                            "ADDED".equals(step.adaptationAction())
                            || "SUBSTITUTED".equals(step.adaptationAction());
                    if (!isExcluded) {
                        var outcomes =
                                agentOutcomes.computeIfAbsent(step.workerName(), k -> new LinkedHashMap<>());
                        outcomes.merge(step.stepOutcome().name(), 1, Integer::sum);
                    }
                    caseNum++;
                    var line =
                            "  %d. [score: %.2f] problem: \"%s\" → agent: \"%s\" → %s"
                                    .formatted(
                                            caseNum,
                                            exp.similarityScore(),
                                            truncate(exp.problem(), 60),
                                            step.workerName(),
                                            step.stepOutcome());
                    if (step.adaptationAction() != null
                        && !"RETAINED".equals(step.adaptationAction())) {
                        line += step.adaptationReason() != null
                                ? " [%s: %s]".formatted(step.adaptationAction(), step.adaptationReason())
                                : " [%s]".formatted(step.adaptationAction());
                    }
                    caseDetails.append(line).append('\n');
                }
            }
            if (exp.planTrace().isEmpty()
                || exp.planTrace().stream()
                      .noneMatch(s -> capabilityName.equals(s.capabilityName()))) {
                caseNum++;
                caseDetails.append(
                        "  %d. [score: %.2f] problem: \"%s\" → %s%n"
                                .formatted(
                                        caseNum,
                                        exp.similarityScore(),
                                        truncate(exp.problem(), 60),
                                        exp.outcome() != null ? exp.outcome() : "UNKNOWN"));
            }
        }

        if (caseNum == 0) {
            return null;
        }

        var sb = new StringBuilder();
        sb.append(
                "Historical context (%d similar past cases for capability \"%s\"):\n"
                        .formatted(caseNum, capabilityName));

        if (!agentOutcomes.isEmpty()) {
            sb.append("\nOutcomes by agent:\n");
            agentOutcomes.forEach(
                    (agent, outcomes) -> {
                        int total     = outcomes.values().stream().mapToInt(Integer::intValue).sum();
                        int successes = outcomes.getOrDefault("SUCCESS", 0);
                        int pct       = total > 0 ? (int) Math.round(100.0 * successes / total) : 0;
                        var parts     = new StringJoiner(", ");
                        outcomes.forEach((outcome, count) -> parts.add(count + " " + outcome));
                        sb.append(
                                "  \"%s\": %d cases — %s (%d%% success)%n"
                                        .formatted(agent, total, parts, pct));
                    });
        }

        sb.append("\nCase details:\n");
        sb.append(caseDetails);
        return sb.toString().stripTrailing();
    }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max - 3) + "...";
  }
}
