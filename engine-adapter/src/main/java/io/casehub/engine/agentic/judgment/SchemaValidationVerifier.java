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
package io.casehub.engine.agentic.judgment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class SchemaValidationVerifier implements JudgmentVerifier {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String id() {
    return "schema-validation";
  }

  @Override
  public VerificationResult verify(VerificationContext context) {
    if (context.decision() == null) {
      return new VerificationResult.Rejected("Decision is null");
    }

    Class<?> resolutionType = context.target().resolutionType();
    if (resolutionType == null) {
      return new VerificationResult.Accepted();
    }

    try {
      JsonNode decisionNode = MAPPER.readTree(context.decision());
      if (decisionNode.isNull() || decisionNode.isMissingNode()) {
        return new VerificationResult.Rejected("Decision serializes to null");
      }
      MAPPER.treeToValue(decisionNode, resolutionType);
      return new VerificationResult.Accepted();
    } catch (Exception e) {
      return new VerificationResult.InsufficientEvidence(
          "Decision does not conform to resolution type " + resolutionType.getSimpleName(),
          List.of(resolutionType.getSimpleName() + ": " + e.getMessage()));
    }
  }
}
