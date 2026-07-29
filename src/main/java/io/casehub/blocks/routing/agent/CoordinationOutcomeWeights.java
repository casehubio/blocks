package io.casehub.blocks.routing.agent;

import java.util.Map;

/**
 * SPI for case-level outcome weights used by {@link CoordinationSignalProvider}.
 * Returns {@code Map<String, Double>} mapping case outcome labels to score weights.
 * Domain repos override {@link DefaultCoordinationOutcomeWeights} with {@code @ApplicationScoped}.
 */
public interface CoordinationOutcomeWeights {
  Map<String, Double> weights();
}
