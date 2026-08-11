package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalProvider;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionAxis;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DispositionAwareRouting implements RoutingSignalProvider {

  static final String CONTEXT_KEY = "_routing";
  static final String DISPOSITION_KEY = "disposition";

  @Override
  public String id() {
    return "disposition";
  }

  @Override
  public @Nullable RoutingSignal evaluate(AgentRoutingContext context,
                                           List<AgentCandidate> eligible) {
    DispositionProfile profile = extractProfile(context.caseContext(), context.capabilityName());
    if (profile == null || profile.desired().isEmpty()) {
      return null;
    }

    Map<String, RoutingSignal.CandidateSignal> candidates = new HashMap<>();
    for (var candidate : eligible) {
      if (candidate.agentDescriptor() == null) continue;
      AgentDisposition disposition = candidate.agentDescriptor().disposition();
      if (disposition == null) continue;

      double score = score(profile, disposition);
      candidates.put(candidate.workerId(),
          new RoutingSignal.CandidateSignal.Score(score, "disposition match"));
    }

    return candidates.isEmpty() ? null : new RoutingSignal(candidates);
  }

    static double score(DispositionProfile profile, AgentDisposition disposition) {
        double totalWeight   = 0.0;
        double weightedMatch = 0.0;

        for (var entry : profile.desired().entrySet()) {
            DispositionAxis axis         = entry.getKey();
            String          desiredValue = entry.getValue();
            double          weight       = profile.weight(axis);
            totalWeight += weight;

            var actual = disposition.primaryTerm(axis);
            if (actual == null) {
                weightedMatch += weight * 0.5;
            } else if (desiredValue.equals(actual)) {
                weightedMatch += weight;
            }
        }

        return totalWeight > 0.0 ? weightedMatch / totalWeight : 0.0;
    }

  static @Nullable DispositionProfile extractProfile(JsonNode caseContext,
                                                      String capabilityName) {
    if (caseContext == null || caseContext.isNull() || caseContext.isMissingNode()) {
      return null;
    }

    JsonNode routing = caseContext.path(CONTEXT_KEY);
    if (routing.isMissingNode()) return null;

    JsonNode dispositionNode = routing.path(DISPOSITION_KEY);
    if (dispositionNode.isMissingNode()) return null;

    JsonNode profileNode = dispositionNode.path(capabilityName);
    if (profileNode.isMissingNode()) {
      profileNode = dispositionNode.path("default");
    }
    if (profileNode.isMissingNode() || !profileNode.isObject()) return null;

    return parseProfile(profileNode);
  }

  private static @Nullable DispositionProfile parseProfile(JsonNode node) {
    Map<DispositionAxis, String> desired = new EnumMap<>(DispositionAxis.class);
    Map<DispositionAxis, Double> weights = new EnumMap<>(DispositionAxis.class);

    for (DispositionAxis axis : DispositionAxis.values()) {
      JsonNode axisNode = node.path(axis.jsonKey());
      if (axisNode.isMissingNode()) continue;

      if (axisNode.isTextual()) {
        desired.put(axis, axisNode.asText());
      } else if (axisNode.isObject()) {
        JsonNode value = axisNode.path("value");
        if (value.isTextual()) {
          desired.put(axis, value.asText());
        }
        JsonNode weight = axisNode.path("weight");
        if (weight.isNumber()) {
          weights.put(axis, weight.asDouble());
        }
      }
    }

    return desired.isEmpty() ? null : new DispositionProfile(desired, weights);
  }
}
