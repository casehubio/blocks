package io.casehub.blocks.agentic.social.goal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalEscalationConfigTest {

    @Test
    void defaults_returnsValidConfig() {
        var config = GoalEscalationConfig.defaults();
        assertThat(config.escalationSalienceThreshold()).isEqualTo(0.6);
        assertThat(config.minAxisAlignmentWeight()).isEqualTo(0.3);
        assertThat(config.crossAxisMinWeight()).isEqualTo(0.3);
        assertThat(config.minCrossAxisCount()).isEqualTo(2);
        assertThat(config.escalationCycles()).isEqualTo(2);
        assertThat(config.demotionCycles()).isEqualTo(2);
        assertThat(config.maxPrimaryDriveGoals()).isEqualTo(1);
    }

    @Test
    void rejectsSalienceOutOfRange() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                1.5, 0.3, 0.3, 2, 2, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSalience() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                -0.1, 0.3, 0.3, 2, 2, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinCrossAxisCountBelow2() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                0.6, 0.3, 0.3, 1, 2, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEscalationCyclesBelow1() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                0.6, 0.3, 0.3, 2, 0, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDemotionCyclesBelow1() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                0.6, 0.3, 0.3, 2, 2, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMaxPrimaryDriveGoals() {
        assertThatThrownBy(() -> new GoalEscalationConfig(
                0.6, 0.3, 0.3, 2, 2, 2, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBoundaryValues() {
        var config = new GoalEscalationConfig(0.0, 0.0, 0.0, 2, 1, 1, 0);
        assertThat(config.escalationSalienceThreshold()).isEqualTo(0.0);
        assertThat(config.maxPrimaryDriveGoals()).isEqualTo(0);
    }
}
