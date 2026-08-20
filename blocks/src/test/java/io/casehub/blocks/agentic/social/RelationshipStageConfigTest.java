package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationshipStageConfigTest {

    @Test
    void defaultsHaveFiveTiers() {
        var config = RelationshipStageConfig.defaults();
        assertThat(config.tiers()).hasSize(5);
        assertThat(config.tiers().get(0).name()).isEqualTo("stranger");
        assertThat(config.tiers().get(4).name()).isEqualTo("confidant");
    }

    @Test
    void resolveStageReturnsHighestMatchingTier() {
        var config = RelationshipStageConfig.defaults();
        assertThat(config.resolveStage(0.0)).isEqualTo("stranger");
        assertThat(config.resolveStage(0.19)).isEqualTo("stranger");
        assertThat(config.resolveStage(0.2)).isEqualTo("acquaintance");
        assertThat(config.resolveStage(0.5)).isEqualTo("familiar");
        assertThat(config.resolveStage(0.8)).isEqualTo("confidant");
        assertThat(config.resolveStage(1.0)).isEqualTo("confidant");
    }

    @Test
    void stageTierRejectsInvalidThreshold() {
        assertThatThrownBy(() -> new StageTier("bad", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StageTier("bad", 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stageTierRejectsNullName() {
        assertThatThrownBy(() -> new StageTier(null, 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyTiersRejected() {
        assertThatThrownBy(() -> new RelationshipStageConfig(
                List.of(), 0.01, 1.0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeDecayRateRejected() {
        assertThatThrownBy(() -> new RelationshipStageConfig(
                List.of(new StageTier("s", 0.0)), -0.01, 1.0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tiersAreImmutable() {
        var config = RelationshipStageConfig.defaults();
        assertThatThrownBy(() -> config.tiers().add(new StageTier("extra", 0.9)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
