package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.AgentDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CivilityConstraintTest {

    private static final AgentDescriptor DESCRIPTOR = mock(AgentDescriptor.class);

    // --- MinimumGapConstraint ---

    @Test
    void gapPermittedWhenEnoughTimeElapsed() {
        var constraint = new MinimumGapConstraint(Duration.ofMinutes(5));
        var ctx = new InitiationContext(
                Instant.now().minus(Duration.ofMinutes(10)), 0, 0, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Permitted.class);
    }

    @Test
    void gapDeniedWhenTooSoon() {
        var constraint = new MinimumGapConstraint(Duration.ofMinutes(5));
        var ctx = new InitiationContext(
                Instant.now().minus(Duration.ofMinutes(2)), 0, 0, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Denied.class);
    }

    @Test
    void gapPermittedOnFirstInitiation() {
        var constraint = new MinimumGapConstraint(Duration.ofMinutes(5));
        var ctx = new InitiationContext(Instant.EPOCH, 0, 0, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Permitted.class);
    }

    // --- MaxPerWindowConstraint ---

    @Test
    void windowPermittedWhenUnderLimit() {
        var constraint = new MaxPerWindowConstraint(3);
        var ctx = new InitiationContext(Instant.now(), 2, 0, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Permitted.class);
    }

    @Test
    void windowDeniedWhenAtLimit() {
        var constraint = new MaxPerWindowConstraint(3);
        var ctx = new InitiationContext(Instant.now(), 3, 0, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Denied.class);
    }

    @Test
    void windowPermittedWhenZeroInitiations() {
        var constraint = new MaxPerWindowConstraint(3);
        var ctx = new InitiationContext(Instant.EPOCH, 0, 0, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Permitted.class);
    }

    // --- ConsecutiveInitiationCooldownConstraint ---

    @Test
    void cooldownPermittedWhenUnderLimit() {
        var constraint = new ConsecutiveInitiationCooldownConstraint(2);
        var ctx = new InitiationContext(Instant.now(), 0, 1, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Permitted.class);
    }

    @Test
    void cooldownDeniedWhenAtLimit() {
        var constraint = new ConsecutiveInitiationCooldownConstraint(2);
        var ctx = new InitiationContext(Instant.now(), 0, 2, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Denied.class);
    }

    @Test
    void cooldownPermittedWhenZeroConsecutive() {
        var constraint = new ConsecutiveInitiationCooldownConstraint(2);
        var ctx = new InitiationContext(Instant.now(), 0, 0, DESCRIPTOR);
        assertThat(constraint.permitInitiation(ctx)).isInstanceOf(CivilityCheck.Permitted.class);
    }
}
