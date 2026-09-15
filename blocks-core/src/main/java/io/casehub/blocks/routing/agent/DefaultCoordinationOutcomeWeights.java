package io.casehub.blocks.routing.agent;

import java.util.Map;

public class DefaultCoordinationOutcomeWeights implements CoordinationOutcomeWeights {

  private static final Map<String, Double> WEIGHTS =
      Map.of("COMPLETED", 1.0, "FAULTED", 0.2, "CANCELLED", 0.0);

  @Override
  public Map<String, Double> weights() {
    return WEIGHTS;
  }
}
