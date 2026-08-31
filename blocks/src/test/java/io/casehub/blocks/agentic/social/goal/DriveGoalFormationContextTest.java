package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveGoalFormationContextTest {

    @Test
    void validContext() {
        var ctx = new DriveGoalFormationContext(
                "a1", "t1", DriveAxis.CURIOSITY, 0.7, "test trigger", List.of(), 3);
        assertThat(ctx.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(ctx.intensity()).isEqualTo(0.7);
    }

    @Test
    void rejectsNegativeIntensity() {
        assertThatThrownBy(() -> new DriveGoalFormationContext(
                "a1", "t1", DriveAxis.CURIOSITY, -0.1, "trigger", List.of(), 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIntensityAboveOne() {
        assertThatThrownBy(() -> new DriveGoalFormationContext(
                "a1", "t1", DriveAxis.CURIOSITY, 1.1, "trigger", List.of(), 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void existingGoalsDefensivelyCopied() {
        var goals = new java.util.ArrayList<io.casehub.eidos.api.AgentGoal>(List.of());
        var ctx = new DriveGoalFormationContext(
                "a1", "t1", DriveAxis.CURIOSITY, 0.5, "trigger", goals, 3);
        assertThatThrownBy(() -> ctx.existingGoals().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
