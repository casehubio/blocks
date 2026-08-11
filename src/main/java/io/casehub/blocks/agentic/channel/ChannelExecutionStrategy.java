package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.ArrayList;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

public sealed interface ChannelExecutionStrategy<T>
        permits ChannelExecutionStrategy.Conversation, ChannelExecutionStrategy.FanIn, ChannelExecutionStrategy.Barrier {

    Uni<ExecutionResult> run(ChannelBinding binding,
                             ExecutionModel<T> model,
                             T initialContext,
                             MessageDispatcher dispatcher);


    record Conversation<T>(
            ConversationConfig<T> config,
            io.casehub.blocks.agentic.termination.TerminationCondition<io.casehub.blocks.conversation.ConversationState> conversationTermination,
            java.util.function.Function<io.casehub.blocks.agentic.AgentRef, io.casehub.blocks.conversation.orchestration.AgentParticipant> resolvedParticipantMapper
    ) implements ChannelExecutionStrategy<T> {
        @Override
        public Uni<ExecutionResult> run(ChannelBinding binding,
                                        ExecutionModel<T> model,
                                        T initialContext,
                                        MessageDispatcher dispatcher) {
            return new ConversationChannelAdapter<>(this)
                           .execute(binding, model, initialContext, dispatcher);
        }
    }

    record FanIn<T>(
            AgentInvoker<T> agentInvoker,
            Function<AgentResult, MessageDispatch.Builder> resultMapper,
            @Nullable Duration executionTimeout
    ) implements ChannelExecutionStrategy<T> {

        public FanIn(AgentInvoker<T> agentInvoker,
                     Function<AgentResult, MessageDispatch.Builder> resultMapper) {
            this(agentInvoker, resultMapper, null);
        }

        @Override
        public Uni<ExecutionResult> run(ChannelBinding binding,
                                        ExecutionModel<T> model,
                                        T initialContext,
                                        MessageDispatcher dispatcher) {
            return Uni.createFrom().item(() -> {
                var candidates = model.candidateSupplier().get();
                var results    = new ArrayList<AgentResult>();
                for (var candidate : candidates) {
                    var ref = candidate.ref();
                    var result = invokeWithTimeout(ref, initialContext);
                    results.add(result);
                    var dispatch = resultMapper.apply(result)
                                               .channelId(binding.channelId())
                                               .sender(ref.name())
                                               .build();
                    dispatcher.dispatch(dispatch);
                }
                return (ExecutionResult) new ExecutionResult.Completed(results);
            });
        }

        private AgentResult invokeWithTimeout(AgentRef ref, T context) {
            try {
                var uni = agentInvoker.invoke(ref, context);
                if (executionTimeout == null) {
                    return uni.await().indefinitely();
                }
                return uni.runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool())
                        .await().atMost(executionTimeout);
            } catch (io.smallrye.mutiny.TimeoutException e) {
                return AgentResult.timeout(ref);
            }
        }
    }

    record Barrier<T>(
            AgentInvoker<T> agentInvoker,
            Function<AgentResult, MessageDispatch.Builder> resultMapper,
            @Nullable Duration executionTimeout
    ) implements ChannelExecutionStrategy<T> {

        public Barrier(AgentInvoker<T> agentInvoker,
                       Function<AgentResult, MessageDispatch.Builder> resultMapper) {
            this(agentInvoker, resultMapper, null);
        }

        @Override
        public Uni<ExecutionResult> run(ChannelBinding binding,
                                        ExecutionModel<T> model,
                                        T initialContext,
                                        MessageDispatcher dispatcher) {
            return Uni.createFrom().item(() -> {
                var candidates = model.candidateSupplier().get();
                var results    = new ArrayList<AgentResult>();
                for (var candidate : candidates) {
                    var ref = candidate.ref();
                    var result = invokeWithTimeout(ref, initialContext);
                    results.add(result);
                    var dispatch = resultMapper.apply(result)
                                               .channelId(binding.channelId())
                                               .sender(ref.name())
                                               .build();
                    dispatcher.dispatch(dispatch);
                }
                return (ExecutionResult) new ExecutionResult.Completed(results);
            });
        }

        private AgentResult invokeWithTimeout(AgentRef ref, T context) {
            try {
                var uni = agentInvoker.invoke(ref, context);
                if (executionTimeout == null) {
                    return uni.await().indefinitely();
                }
                return uni.runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool())
                        .await().atMost(executionTimeout);
            } catch (io.smallrye.mutiny.TimeoutException e) {
                return AgentResult.timeout(ref);
            }
        }
    }
}
