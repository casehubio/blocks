package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributedStateTest {

    @Test
    void validAttributedState() {
        var state = new AttributedState("deployment_risk",
                "subject thinks deployments are risky",
                0.8, 3, Instant.now(), BdiDimension.BELIEF);
        assertThat(state.key()).isEqualTo("deployment_risk");
        assertThat(state.confidence()).isEqualTo(0.8);
        assertThat(state.entrenchment()).isEqualTo(3);
        assertThat(state.dimension()).isEqualTo(BdiDimension.BELIEF);
    }

    @Test
    void nullKeyThrows() {
        assertThatThrownBy(() -> new AttributedState(null, "desc", 0.5, 0, Instant.now(), BdiDimension.BELIEF))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullDescriptionThrows() {
        assertThatThrownBy(() -> new AttributedState("key", null, 0.5, 0, Instant.now(), BdiDimension.BELIEF))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void confidenceBelowZeroThrows() {
        assertThatThrownBy(() -> new AttributedState("key", "desc", -0.1, 0, Instant.now(), BdiDimension.BELIEF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidenceAboveOneThrows() {
        assertThatThrownBy(() -> new AttributedState("key", "desc", 1.1, 0, Instant.now(), BdiDimension.BELIEF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeEntrenchmentThrows() {
        assertThatThrownBy(() -> new AttributedState("key", "desc", 0.5, -1, Instant.now(), BdiDimension.BELIEF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullDimensionThrows() {
        assertThatThrownBy(() -> new AttributedState("key", "desc", 0.5, 0, Instant.now(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLastReinforcedThrows() {
        assertThatThrownBy(() -> new AttributedState("key", "desc", 0.5, 0, null, BdiDimension.DESIRE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void boundaryConfidenceZeroAccepted() {
        var state = new AttributedState("key", "desc", 0.0, 0, Instant.now(), BdiDimension.INTENTION);
        assertThat(state.confidence()).isZero();
    }

    @Test
    void boundaryConfidenceOneAccepted() {
        var state = new AttributedState("key", "desc", 1.0, 0, Instant.now(), BdiDimension.BELIEF);
        assertThat(state.confidence()).isEqualTo(1.0);
    }
}
