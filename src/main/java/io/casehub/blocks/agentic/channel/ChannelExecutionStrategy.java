package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.function.Function;

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
            Function<AgentResult, MessageDispatch.Builder> resultMapper
    ) implements ChannelExecutionStrategy<T> {

        @Override
        public Uni<ExecutionResult> run(ChannelBinding binding,
                                        ExecutionModel<T> model,
                                        T initialContext,
                                        MessageDispatcher dispatcher) {
            return Uni.createFrom().item(() -> {
                var candidates = model.candidateSupplier().get();
                var results    = new ArrayList<AgentResult>();
                for (var candidate : candidates) {
                    var ref    = candidate.ref();
                    var result = agentInvoker.invoke(ref, initialContext).await().indefinitely();
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
    }

    record Barrier<T>(
            AgentInvoker<T> agentInvoker,
            Function<AgentResult, MessageDispatch.Builder> resultMapper
    ) implements ChannelExecutionStrategy<T> {

        @Override
        public Uni<ExecutionResult> run(ChannelBinding binding,
                                        ExecutionModel<T> model,
                                        T initialContext,
                                        MessageDispatcher dispatcher) {
            return Uni.createFrom().item(() -> {
                var candidates = model.candidateSupplier().get();
                var results    = new ArrayList<AgentResult>();
                for (var candidate : candidates) {
                    var ref    = candidate.ref();
                    var result = agentInvoker.invoke(ref, initialContext).await().indefinitely();
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
    }
}
