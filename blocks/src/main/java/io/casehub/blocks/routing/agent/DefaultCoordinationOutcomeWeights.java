package io.casehub.blocks.routing.agent;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@DefaultBean
@ApplicationScoped
public class DefaultCoordinationOutcomeWeights implements CoordinationOutcomeWeights {

  private static final Map<String, Double> WEIGHTS =
      Map.of("COMPLETED", 1.0, "FAULTED", 0.2, "CANCELLED", 0.0);

  @Override
  public Map<String, Double> weights() {
    return WEIGHTS;
  }
}
