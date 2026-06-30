package io.casehub.blocks.agentic.activation;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MaxIterationsGuardTest {

    @Test
    void activatesWhenBelowMax() {
        var rule = new MaxIterationsGuard<String>(5);
        var agent = AgentRef.worker(mock(Worker.class));
        var ctx = new ActivationContext<>("event", "state",
                agent, 3, Optional.empty(), 0);
        assertThat(rule.shouldActivate(ctx).await().indefinitely()).isTrue();
    }

    @Test
    void deactivatesAtMax() {
        var rule = new MaxIterationsGuard<String>(5);
        var agent = AgentRef.worker(mock(Worker.class));
        var ctx = new ActivationContext<>("event", "state",
                agent, 5, Optional.empty(), 0);
        assertThat(rule.shouldActivate(ctx).await().indefinitely()).isFalse();
    }
}
