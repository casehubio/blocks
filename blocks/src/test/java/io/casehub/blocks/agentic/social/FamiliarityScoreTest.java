package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FamiliarityScoreTest {

    private final RelationshipStageConfig config = RelationshipStageConfig.defaults();

    @Test
    void zeroInteractionsProducesZeroScore() {
        double score = UserModelOrchestrator.computeFamiliarity(0, 0, 0, config, 0);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void allPositiveProducesHighScore() {
        double score = UserModelOrchestrator.computeFamiliarity(20, 0, 0, config, 0);
        assertThat(score).isGreaterThan(0.6);
    }

    @Test
    void allNegativeProducesLowScore() {
        double score = UserModelOrchestrator.computeFamiliarity(0, 20, 0, config, 0);
        assertThat(score).isLessThan(0.3);
    }

    @Test
    void decayReducesScoreOverTime() {
        double fresh = UserModelOrchestrator.computeFamiliarity(10, 0, 0, config, 0);
        double decayed = UserModelOrchestrator.computeFamiliarity(10, 0, 0, config, 100);
        assertThat(decayed).isLessThan(fresh);
    }

    @Test
    void negativeWeightDampensNegativeSignals() {
        double withDampening = UserModelOrchestrator.computeFamiliarity(5, 5, 0, config, 0);
        var noDampConfig = new RelationshipStageConfig(config.tiers(), 0.01, 1.0, 1.0);
        double withoutDampening = UserModelOrchestrator.computeFamiliarity(5, 5, 0, noDampConfig, 0);
        assertThat(withDampening).isGreaterThan(withoutDampening);
    }

    @Test
    void volumeFactorIncreasesWithInteractionCount() {
        double few = UserModelOrchestrator.computeFamiliarity(5, 0, 0, config, 0);
        double many = UserModelOrchestrator.computeFamiliarity(50, 0, 0, config, 0);
        assertThat(many).isGreaterThan(few);
    }

    @Test
    void scoreIsAlwaysInZeroOneRange() {
        double high = UserModelOrchestrator.computeFamiliarity(1000, 0, 0, config, 0);
        double low = UserModelOrchestrator.computeFamiliarity(0, 1000, 0, config, 0);
        assertThat(high).isBetween(0.0, 1.0);
        assertThat(low).isBetween(0.0, 1.0);
    }
}
