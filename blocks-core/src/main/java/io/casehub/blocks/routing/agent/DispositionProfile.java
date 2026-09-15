package io.casehub.blocks.routing.agent;

import io.casehub.eidos.api.DispositionAxis;

import java.util.Map;
import java.util.Objects;

public record DispositionProfile(Map<DispositionAxis, String> desired,
                                  Map<DispositionAxis, Double> weights) {

  public DispositionProfile {
    Objects.requireNonNull(desired, "desired");
    desired = Map.copyOf(desired);
    weights = weights != null ? Map.copyOf(weights) : Map.of();
  }

  public DispositionProfile(Map<DispositionAxis, String> desired) {
    this(desired, Map.of());
  }

  public double weight(DispositionAxis axis) {
    return weights.getOrDefault(axis, 1.0);
  }
}
