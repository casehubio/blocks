package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvokerTest {

    @Test
    void defaultInvokerDispatchesExternalAgent() {
        var agent = AgentRef.external((Object input) ->
                                              CompletableFuture.completedFuture(AgentResult.success(null, "output")));

        var invoker = AgentInvoker.defaultInvoker();
        var result  = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo("output");
    }

    @Test
    void defaultInvokerReturnsFailureForUnsupportedVariant() {
        var agent = AgentRef.worker(
                io.casehub.worker.api.Worker.builder()
                                            .name("test").capabilityName("cap").noFunction().build());

        var invoker = AgentInvoker.defaultInvoker();
        var result  = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.FAILURE);
    }

    @Test
    void defaultInvokerDispatchesComposedAgent() {
        var innerAgent = AgentRef.external((Object input) ->
                                                   CompletableFuture.completedFuture(AgentResult.success(null, "inner-output")));

        var innerModel = new ExecutionModel<>(
                new io.casehub.blocks.agentic.routing.FirstMatchRouting<>(c -> true),
                new io.casehub.blocks.agentic.decomposition.IdentityDecomposition<>(),
                new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                new io.casehub.blocks.agentic.aggregation.PassThrough<>(),
                new io.casehub.blocks.agentic.termination.MaxIterationsTermination<>(1),
                () -> List.of(new io.casehub.blocks.agentic.RoutingCandidate(innerAgent, null)),
                io.casehub.blocks.agentic.FailurePolicy.defaults(),
                List.of(),
                "inner-task"
        );
        var composed = AgentRef.composed(innerModel);

        var invoker = AgentInvoker.defaultInvoker();
        var result  = invoker.invoke(composed, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
    }

    @Test
    void defaultInvokerReturnsFailureForChannelAgent() {
        var agent   = AgentRef.channel(java.util.UUID.randomUUID(), null);
        var invoker = AgentInvoker.defaultInvoker();
        var result  = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.FAILURE);
        assertThat(result.output().toString()).contains("ChannelAgent");
    }

    @Test
    void defaultInvokerReturnsFailureForHumanAgent() {
        var agent   = AgentRef.human(null);
        var invoker = AgentInvoker.defaultInvoker();
        var result  = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.FAILURE);
        assertThat(result.output().toString()).contains("HumanAgent");
    }

    @Test
    void withFallbackDelegatesToFallbackOnUnsupported() {
        var workerAgent = AgentRef.worker(
                io.casehub.worker.api.Worker.builder()
                                            .name("test").capabilityName("cap").noFunction().build());

        AgentInvoker<Object> custom = (agent, state) ->
                                              io.smallrye.mutiny.Uni.createFrom().item(AgentResult.success(agent, "custom-handled"));

        var invoker = AgentInvoker.<Object>defaultInvoker().withFallback(custom);
        var result  = invoker.invoke(workerAgent, "state").await().indefinitely();

        assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo("custom-handled");
    }

    @Test
    void withFallbackDoesNotDelegateOnSuccess() {
        var agent = AgentRef.external((Object input) ->
                                              CompletableFuture.completedFuture(AgentResult.success(null, "primary")));

        AgentInvoker<Object> fallback = (a, s) ->
                                                io.smallrye.mutiny.Uni.createFrom().item(AgentResult.success(a, "fallback"));

        var invoker = AgentInvoker.<Object>defaultInvoker().withFallback(fallback);
        var result  = invoker.invoke(agent, "state").await().indefinitely();

        assertThat(result.output()).isEqualTo("primary");
    }

}
