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

import io.casehub.api.spi.routing.RoutingOutcome;
import java.util.Map;

/**
 * Configurable weights for step-level routing outcomes used by {@link CbrAgentRoutingStrategy}
 * when scoring workers based on historical experiences.
 *
 * <p>Domain repos override the {@link DefaultCbrOutcomeWeights} {@code @DefaultBean} with
 * {@code @ApplicationScoped} to tune how different outcomes influence worker scoring.
 */
public interface CbrOutcomeWeights {
  Map<RoutingOutcome, Double> weights();
}
