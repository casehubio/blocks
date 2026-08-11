package io.casehub.blocks.routing.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCoordinationOutcomeWeightsTest {

  private final CoordinationOutcomeWeights weights = new DefaultCoordinationOutcomeWeights();

  @Test
  void completedWeightsOne() {
    assertThat(weights.weights().get("COMPLETED")).isEqualTo(1.0);
  }

  @Test
  void faultedWeightsPointTwo() {
    assertThat(weights.weights().get("FAULTED")).isEqualTo(0.2);
  }

  @Test
  void cancelledWeightsZero() {
    assertThat(weights.weights().get("CANCELLED")).isEqualTo(0.0);
  }

  @Test
  void weightsAreImmutable() {
    var w = weights.weights();
    assertThat(w).isUnmodifiable();
  }
}
