package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.PlanningConstraints;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticDecompositionContextTest {

    @Test
    void constraintsReturnProvidedPlanningConstraints() {
        var constraints = new PlanningConstraints(
                Duration.ofMinutes(30), 3, Map.of(), Map.of("tokens", 5000));
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0,
                null, null, null, null, null, constraints);

        assertThat(ctx.constraints()).isSameAs(constraints);
    }

    @Test
    void nullConstraintsDefaultToUnconstrained() {
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0,
                null, null, null, null, null, null);

        assertThat(ctx.constraints()).isEqualTo(PlanningConstraints.unconstrained());
        assertThat(ctx.planningConstraints()).isEqualTo(PlanningConstraints.unconstrained());
    }

    @Test
    void threeArgConstructorReturnsUnconstrainedConstraints() {
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0);

        assertThat(ctx.constraints()).isEqualTo(PlanningConstraints.unconstrained());
    }

    @Test
    void fourArgConstructorReturnsUnconstrainedConstraints() {
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0, "hint");

        assertThat(ctx.constraints()).isEqualTo(PlanningConstraints.unconstrained());
    }
}
