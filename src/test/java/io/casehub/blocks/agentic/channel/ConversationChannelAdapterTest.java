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
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.orchestration.AgentParticipant;
import io.casehub.blocks.conversation.orchestration.PromptAssembler;
import io.casehub.blocks.conversation.orchestration.ResponseMessageBuilder;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationChannelAdapterTest {

    @Test
    void mapsModelCandidatesToParticipants() {
        var agent1 = AgentRef.external("analyst", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "analysis")));
        var agent2 = AgentRef.external("critic", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "critique")));

        var invocations = new AtomicInteger();
        AgentInvoker<String> countingInvoker = (agent, state) -> {
            invocations.incrementAndGet();
            return io.smallrye.mutiny.Uni.createFrom().item(
                    AgentResult.success(agent, "response-" + invocations.get()));
        };

        var config = new ConversationConfig<String>(
                (agent, drain, state) -> "prompt for " + agent.agentId(),
                (agent, result, state) -> mockMessage(agent.agentId(), result.output().toString()),
                s -> mockMessage("system", s),
                null, countingInvoker, null, null, null);

        @SuppressWarnings("unchecked")
        var termination = (TerminationCondition<ConversationState>)
                (TerminationCondition<?>) new MaxIterationsTermination<>(1);

        var strategy = new ChannelExecutionStrategy.Conversation<>(
                config, termination,
                agent -> new AgentParticipant(agent, agent.name(), null));

        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        var model = buildModel(PatternType.DEBATE, agent1, agent2);
        var dispatcher = mock(MessageDispatcher.class);

        var result = strategy.run(binding, model, "debate topic", dispatcher)
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void mapsCompletedOutcomeToCompletedResult() {
        var agent = AgentRef.external("solo", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "done")));

        AgentInvoker<String> invoker = (a, s) ->
                io.smallrye.mutiny.Uni.createFrom().item(AgentResult.success(a, "ok"));

        var config = new ConversationConfig<String>(
                (ag, drain, state) -> "prompt",
                (ag, result, state) -> mockMessage(ag.agentId(), "reply"),
                s -> mockMessage("trigger", s),
                null, invoker, null, null, null);

        @SuppressWarnings("unchecked")
        var termination = (TerminationCondition<ConversationState>)
                (TerminationCondition<?>) new MaxIterationsTermination<>(1);

        var strategy = new ChannelExecutionStrategy.Conversation<>(
                config, termination,
                a -> new AgentParticipant(a, "role", null));

        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        var model = buildModel(null, agent);
        var dispatcher = mock(MessageDispatcher.class);

        var result = strategy.run(binding, model, "go", dispatcher)
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        var completed = (ExecutionResult.Completed) result;
        assertThat(completed.result()).isInstanceOf(ConversationState.class);
    }

    private static MessageView mockMessage(String sender, String content) {
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn(sender);
        when(msg.content()).thenReturn(content);
        when(msg.correlationId()).thenReturn("corr-" + System.nanoTime());
        when(msg.id()).thenReturn(System.nanoTime());
        when(msg.type()).thenReturn(null);
        when(msg.createdAt()).thenReturn(Instant.now());
        when(msg.topic()).thenReturn("general");
        return msg;
    }

    private static ExecutionModel<String> buildModel(PatternType patternType, AgentRef... agents) {
        var candidates = java.util.Arrays.stream(agents)
                .map(a -> new RoutingCandidate(a, null)).toList();
        return new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), () -> candidates,
                FailurePolicy.defaults(), List.of(), "test", patternType);
    }
}
