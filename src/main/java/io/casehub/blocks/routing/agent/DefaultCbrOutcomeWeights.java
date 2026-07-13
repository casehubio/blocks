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
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@DefaultBean
@ApplicationScoped
public class DefaultCbrOutcomeWeights implements CbrOutcomeWeights {

  private static final Map<RoutingOutcome, Double> DEFAULTS =
      Map.of(
          RoutingOutcome.SUCCESS, 1.0,
          RoutingOutcome.GATE_EXPIRED, 0.5,
          RoutingOutcome.GATE_REJECTED, 0.25,
          RoutingOutcome.FAILURE, 0.0);

  @Override
  public Map<RoutingOutcome, Double> weights() {
    return DEFAULTS;
  }
}
