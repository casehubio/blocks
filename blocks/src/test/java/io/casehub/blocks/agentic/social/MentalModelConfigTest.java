package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MentalModelConfigTest {

    @Test
    void defaultConfig() {
        var config = MentalModelConfig.defaults();
        assertThat(config.beliefHalfLife()).isEqualTo(Duration.ofDays(7));
        assertThat(config.desireHalfLife()).isEqualTo(Duration.ofDays(1));
        assertThat(config.intentionHalfLife()).isEqualTo(Duration.ofHours(4));
        assertThat(config.confidenceFloor()).isEqualTo(0.1);
        assertThat(config.projectionFloor()).isEqualTo(0.3);
        assertThat(config.minSignalsForInference()).isEqualTo(3);
        assertThat(config.inferenceCooldown()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.maxSignalsInPrompt()).isEqualTo(20);
        assertThat(config.maxBufferSize()).isEqualTo(100);
        assertThat(config.evictionTimeout()).isEqualTo(Duration.ofHours(24));
        assertThat(config.expectedTickInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.memoryDomain()).isEqualTo("mental-model");
        assertThat(config.caseType()).isEqualTo("mental-model");
    }

    @Test
    void halfLifeForDimension() {
        var config = MentalModelConfig.defaults();
        assertThat(config.halfLifeFor(BdiDimension.BELIEF)).isEqualTo(Duration.ofDays(7));
        assertThat(config.halfLifeFor(BdiDimension.DESIRE)).isEqualTo(Duration.ofDays(1));
        assertThat(config.halfLifeFor(BdiDimension.INTENTION)).isEqualTo(Duration.ofHours(4));
    }
}
