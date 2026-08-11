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

import java.util.Map;

/**
 * Configurable weights for case-level outcomes used by {@link PlanCompositionAnalyser} when
 * scoring workers based on the overall outcome of multi-step plans they participated in.
 *
 * <p>Case-level outcomes (COMPLETED, FAULTED, CANCELLED) use a different vocabulary from
 * step-level outcomes ({@link io.casehub.api.spi.routing.RoutingOutcome}). String keys because
 * case-level outcomes are domain-dependent strings from {@code CaseOutcomeEvent.outcomeLabel()}.
 *
 * <p>Domain repos override the {@link DefaultCbrCaseOutcomeWeights} {@code @DefaultBean} with
 * {@code @ApplicationScoped} to tune how different case outcomes influence plan-fit scoring.
 */
public interface CbrCaseOutcomeWeights {
  Map<String, Double> weights();
}
