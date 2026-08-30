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
package io.casehub.blocks.agentic.judgment;

import static org.junit.jupiter.api.Assertions.*;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;
import io.casehub.api.spi.judgment.EvidenceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgmentDecisionTest {

  private static final CallerIdentity LLM_CALLER = CallerIdentity.of("claude", "llm");

  @Test
  void approvedDecision() {
    var evidence = List.of(Evidence.of("reasoning", EvidenceType.REASONING, "looks good"));
    var decision = new JudgmentDecision.Approved("result-data", evidence, LLM_CALLER);

    assertInstanceOf(JudgmentDecision.class, decision);
    assertEquals("result-data", decision.result());
    assertEquals(1, decision.evidence().size());
    assertEquals("claude", decision.caller().callerId());
  }

  @Test
  void rejectedDecision() {
    var decision =
        new JudgmentDecision.Rejected("needs more detail", List.of(), LLM_CALLER);

    assertEquals("needs more detail", decision.feedback());
    assertTrue(decision.evidence().isEmpty());
  }

  @Test
  void escalatedDecision() {
    var decision = new JudgmentDecision.Escalated("beyond my capability", LLM_CALLER);

    assertEquals("beyond my capability", decision.reason());
  }

  @Test
  void sealedTypeExhaustiveness() {
    JudgmentDecision decision = new JudgmentDecision.Approved("ok", List.of(), LLM_CALLER);
    String result =
        switch (decision) {
          case JudgmentDecision.Approved a -> "approved:" + a.result();
          case JudgmentDecision.Rejected r -> "rejected:" + r.feedback();
          case JudgmentDecision.Escalated e -> "escalated:" + e.reason();
        };
    assertEquals("approved:ok", result);
  }
}
