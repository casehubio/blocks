package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FanInStrategyTest {

    @Test
    void invokesAllAgentsAndDispatchesResults() {
        var dispatcher = mock(MessageDispatcher.class);
        var agent1 = AgentRef.external("voter-1", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "yes")));
        var agent2 = AgentRef.external("voter-2", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "no")));

        var resultMapper = resultToDispatchBuilder();
        var strategy = new ChannelExecutionStrategy.FanIn<String>(
                AgentInvoker.defaultInvoker(), resultMapper);
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.COLLECT);
        var model = buildModel(agent1, agent2);

        var result = strategy.run(binding, model, "vote please", dispatcher)
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        var captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(dispatcher, times(2)).dispatch(captor.capture());
        var dispatches = captor.getAllValues();
        assertThat(dispatches).allMatch(d -> d.channelId().equals(binding.channelId()));
        assertThat(dispatches.stream().map(MessageDispatch::sender).toList())
                .containsExactlyInAnyOrder("voter-1", "voter-2");
    }

    @Test
    void completedResultCarriesAgentResults() {
        var dispatcher = mock(MessageDispatcher.class);
        var agent = AgentRef.external("v1", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "approved")));

        var strategy = new ChannelExecutionStrategy.FanIn<String>(
                AgentInvoker.defaultInvoker(), resultToDispatchBuilder());
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.COLLECT);
        var model = buildModel(agent);

        var result = (ExecutionResult.Completed) strategy.run(binding, model, "vote", dispatcher)
                .await().indefinitely();

        assertThat(result.result()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        var results = (List<AgentResult>) result.result();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).output()).isEqualTo("approved");
    }

    @Test
    void continuesWhenAgentFails() {
        var dispatcher = mock(MessageDispatcher.class);
        var failing = AgentRef.external("fail", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.failure(null, "error")));
        var passing = AgentRef.external("pass", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));

        var strategy = new ChannelExecutionStrategy.FanIn<String>(
                AgentInvoker.defaultInvoker(), resultToDispatchBuilder());
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.COLLECT);
        var model = buildModel(failing, passing);

        var result = strategy.run(binding, model, "go", dispatcher)
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        verify(dispatcher, times(2)).dispatch(any());
    }

    @Test
    void timeoutProducesTimeoutResultForSlowAgent() {
        var dispatcher = mock(MessageDispatcher.class);
        var slow = AgentRef.external("slow", (Object ctx) -> {
            var future = new CompletableFuture<AgentResult>();
            new Thread(() -> {
                try {Thread.sleep(5000);} catch (InterruptedException ignored) {}
                future.complete(AgentResult.success(null, "late"));
            }).start();
            return future;
        });
        var fast = AgentRef.external("fast", (Object ctx) ->
                                                     CompletableFuture.completedFuture(AgentResult.success(null, "ok")));

        var strategy = new ChannelExecutionStrategy.FanIn<String>(
                AgentInvoker.defaultInvoker(), resultToDispatchBuilder(),
                java.time.Duration.ofMillis(100));
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.COLLECT);
        var model   = buildModel(slow, fast);

        var result = (ExecutionResult.Completed) strategy.run(binding, model, "go", dispatcher)
                                                         .await().indefinitely();

        @SuppressWarnings("unchecked")
        var results = (List<AgentResult>) result.result();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(AgentResult.AgentResultStatus.TIMEOUT);
        assertThat(results.get(1).status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
    }

    @Test
    void noTimeoutAwaitsNormally() {
        var dispatcher = mock(MessageDispatcher.class);
        var agent = AgentRef.external("a1", (Object ctx) ->
                                                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));

        var strategy = new ChannelExecutionStrategy.FanIn<String>(
                AgentInvoker.defaultInvoker(), resultToDispatchBuilder());
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.COLLECT);
        var model   = buildModel(agent);

        var result = strategy.run(binding, model, "go", dispatcher).await().indefinitely();
        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }


    private static java.util.function.Function<AgentResult, MessageDispatch.Builder> resultToDispatchBuilder() {
        return result -> MessageDispatch.builder()
                .content(result.output() != null ? result.output().toString() : "")
                .type(MessageType.STATUS)
                .actorType(ActorType.AGENT);
    }

    private static ExecutionModel<String> buildModel(AgentRef... agents) {
        var candidates = java.util.Arrays.stream(agents)
                .map(a -> new RoutingCandidate(a, null)).toList();
        return new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), () -> candidates,
                FailurePolicy.defaults(), List.of(), "test");
    }
}
