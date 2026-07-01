package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvokerTest {

    @Test
    void defaultInvokerDispatchesExternalAgent() {
        var agent = AgentRef.external((Object input) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "output")));

        var invoker = AgentInvoker.defaultInvoker();
        var result = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo("output");
    }

    @Test
    void defaultInvokerReturnsFailureForUnsupportedVariant() {
        var agent = AgentRef.worker(
                io.casehub.worker.api.Worker.builder()
                        .name("test").capabilityName("cap").noFunction().build());

        var invoker = AgentInvoker.defaultInvoker();
        var result = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.FAILURE);
    }
}
