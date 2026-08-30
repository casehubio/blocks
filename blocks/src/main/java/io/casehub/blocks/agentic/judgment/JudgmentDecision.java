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

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;
import java.util.List;

/**
 * Sealed decision type from inline judgment evaluation. Uses engine foundation types for evidence
 * and caller identity.
 *
 * <p>Refs blocks#219, engine#994, engine#1009.
 */
public sealed interface JudgmentDecision {

  record Approved(Object result, List<Evidence> evidence, CallerIdentity caller)
      implements JudgmentDecision {}

  record Rejected(String feedback, List<Evidence> evidence, CallerIdentity caller)
      implements JudgmentDecision {}

  record Escalated(String reason, CallerIdentity caller) implements JudgmentDecision {}
}
