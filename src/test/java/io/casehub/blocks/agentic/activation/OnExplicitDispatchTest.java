package io.casehub.blocks.agentic.activation;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OnExplicitDispatchTest {

    @Test
    void alwaysActivates() {
        var rule = new OnExplicitDispatch<String>();
        var agent = AgentRef.worker(mock(Worker.class));
        var ctx = new ActivationContext<>("event", "state", agent,
                0, Optional.empty(), 0);
        assertThat(rule.shouldActivate(ctx).await().indefinitely()).isTrue();
    }
}
