package io.casehub.blocks.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TrustEvolutionConfigTest {

    @Test
    void constructsWithAllFields() {
        var config = new TrustEvolutionConfig(
            List.of(new TrustEvolutionConfig.TrustEventMapping(
                "STEAL", AttestationVerdict.FLAGGED, 0.9, 0.5)),
            new TrustEvolutionConfig.ScoringConfig(30, 1.5),
            new TrustEvolutionConfig.ConsolidationConfig("trust-score", 0.15),
            new TrustEvolutionConfig.LevelConfig(0.7, 0.4, 0.2));

        assertThat(config.events()).hasSize(1);
        assertThat(config.events().get(0).actionType()).isEqualTo("STEAL");
        assertThat(config.events().get(0).verdict()).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(config.scoring().decayHalfLifeDays()).isEqualTo(30);
        assertThat(config.scoring().negativeDecayMultiplier()).isEqualTo(1.5);
        assertThat(config.consolidation().significantChangeThreshold()).isEqualTo(0.15);
        assertThat(config.levels().high()).isEqualTo(0.7);
    }

    @Test
    void findsMappingByActionType() {
        var config = new TrustEvolutionConfig(
            List.of(
                new TrustEvolutionConfig.TrustEventMapping("STEAL", AttestationVerdict.FLAGGED, 0.9, 0.5),
                new TrustEvolutionConfig.TrustEventMapping("GIVE", AttestationVerdict.SOUND, 0.7, 0.3)),
            new TrustEvolutionConfig.ScoringConfig(30, 1.5),
            new TrustEvolutionConfig.ConsolidationConfig("trust-score", 0.15),
            new TrustEvolutionConfig.LevelConfig(0.7, 0.4, 0.2));

        assertThat(config.findMapping("STEAL")).isPresent();
        assertThat(config.findMapping("STEAL").get().verdict()).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(config.findMapping("GIVE")).isPresent();
        assertThat(config.findMapping("GIVE").get().verdict()).isEqualTo(AttestationVerdict.SOUND);
        assertThat(config.findMapping("MOVE")).isEmpty();
    }

    @Test
    void eventsListIsImmutable() {
        var config = new TrustEvolutionConfig(
            new java.util.ArrayList<>(List.of(
                new TrustEvolutionConfig.TrustEventMapping("STEAL", AttestationVerdict.FLAGGED, 0.9, 0.5))),
            new TrustEvolutionConfig.ScoringConfig(30, 1.5),
            new TrustEvolutionConfig.ConsolidationConfig("trust-score", 0.15),
            new TrustEvolutionConfig.LevelConfig(0.7, 0.4, 0.2));

        assertThatThrownBy(() -> config.events().add(
            new TrustEvolutionConfig.TrustEventMapping("GIVE", AttestationVerdict.SOUND, 0.7, 0.3)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void scoringConfigRejectsInvalidValues() {
        assertThatThrownBy(() -> new TrustEvolutionConfig.ScoringConfig(0, 1.5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrustEvolutionConfig.ScoringConfig(30, -1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void levelConfigRejectsInvertedThresholds() {
        assertThatThrownBy(() -> new TrustEvolutionConfig.LevelConfig(0.3, 0.5, 0.2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consolidationConfigRejectsOutOfRangeThreshold() {
        assertThatThrownBy(() -> new TrustEvolutionConfig.ConsolidationConfig("trust-score", 1.5))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
