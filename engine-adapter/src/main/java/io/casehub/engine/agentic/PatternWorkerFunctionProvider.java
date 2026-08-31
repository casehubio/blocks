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
package io.casehub.engine.agentic;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.WorkerFunctionProvider;
import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.judgment.EvidenceRequirement;
import io.casehub.api.spi.judgment.EvidenceType;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.engine.agentic.judgment.PatternJudgmentConfig;
import io.casehub.worker.api.WorkerFunction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PatternWorkerFunctionProvider implements WorkerFunctionProvider {

  @Override
  public boolean handles(JsonNode rawWorkerNode) {
    return rawWorkerNode.has("pattern");
  }

  @Override
  public WorkerFunction<?, ?> create(JsonNode rawWorkerNode) {
    JsonNode patternNode = rawWorkerNode.get("pattern");
    String typeName = patternNode.path("type").asText("sequence");
    PatternType patternType = PatternType.valueOf(typeName.toUpperCase());
    boolean checkpointing = patternNode.path("checkpointing").asBoolean(false);

    io.casehub.engine.plan.PlanningConstraints constraints = null;
    if (patternNode.has("constraints")) {
      JsonNode cNode = patternNode.get("constraints");
      java.time.Duration timeBudget =
          cNode.has("timeBudget")
              ? java.time.Duration.parse(cNode.get("timeBudget").asText())
              : null;
      Integer resourceLimit =
          cNode.has("resourceLimit") ? cNode.get("resourceLimit").asInt() : null;
      java.util.Map<String, Integer> costBudgets = new java.util.LinkedHashMap<>();
      if (cNode.has("costBudgets") && cNode.get("costBudgets").isObject()) {
        cNode
            .get("costBudgets")
            .fields()
            .forEachRemaining(e -> costBudgets.put(e.getKey(), e.getValue().asInt()));
      }
      constraints =
          new io.casehub.engine.plan.PlanningConstraints(
              timeBudget, resourceLimit, java.util.Map.of(), costBudgets);
    }

    PatternJudgmentConfig judgmentConfig = null;
    if (patternNode.has("judgment")) {
      judgmentConfig = parseJudgmentConfig(patternNode.get("judgment"));
    }

    return new PatternWorkerFunction(null, patternType, checkpointing, constraints, null, judgmentConfig);
  }

  private PatternJudgmentConfig parseJudgmentConfig(JsonNode judgmentNode) {
    String prompt = judgmentNode.path("prompt").asText(null);
    boolean afterStep = judgmentNode.path("afterStep").asBoolean(false);

    CallerConfig callerConfig = null;
    if (judgmentNode.has("caller")) {
      callerConfig = parseCallerConfig(judgmentNode.get("caller"));
    }

    String verifier = judgmentNode.path("verifier").asText(null);

    List<EvidenceRequirement> evidenceRequirements = new ArrayList<>();
    if (judgmentNode.has("evidence") && judgmentNode.get("evidence").isArray()) {
      for (JsonNode req : judgmentNode.get("evidence")) {
        evidenceRequirements.add(
            new EvidenceRequirement(
                req.path("name").asText(),
                EvidenceType.valueOf(req.path("type").asText()),
                req.path("required").asBoolean(false)));
      }
    }

    PatternJudgmentConfig.JudgmentMode mode = null;
    if (judgmentNode.has("mode")) {
      String modeStr = judgmentNode.get("mode").asText().toUpperCase().replace("-", "_");
      mode = PatternJudgmentConfig.JudgmentMode.valueOf(modeStr);
    }

    return new PatternJudgmentConfig(prompt, callerConfig, verifier, evidenceRequirements, mode, afterStep);
  }

  private CallerConfig parseCallerConfig(JsonNode callerNode) {
    String type = callerNode.path("type").asText("llm");
    return switch (type) {
      case "llm" ->
          new CallerConfig.Llm(
              callerNode.path("model").asText(null),
              callerNode.path("modelName").asText(null),
              callerNode.path("systemPrompt").asText(null));
      case "a2a" ->
          new CallerConfig.A2A(
              callerNode.path("endpoint").asText(null),
              callerNode.path("skill").asText(null),
              callerNode.path("streaming").asBoolean(false));
      default -> new CallerConfig.Any();
    };
  }
}
