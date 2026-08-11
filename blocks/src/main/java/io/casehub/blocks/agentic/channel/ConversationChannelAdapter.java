package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationProjection;
import io.casehub.blocks.conversation.orchestration.AgentParticipant;
import io.casehub.blocks.conversation.orchestration.ConversationOrchestrator;
import io.casehub.blocks.conversation.orchestration.FreeTurnPolicy;
import io.casehub.blocks.conversation.orchestration.PointAddressedTurnPolicy;
import io.casehub.blocks.conversation.orchestration.RoundRobinTurnPolicy;
import io.casehub.blocks.conversation.orchestration.TurnPolicy;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.observation.ObservationResult;
import io.casehub.blocks.summarisation.observation.PartitionedObservationService;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

class ConversationChannelAdapter<T> {

    private static final EventLevel CONVERSATION = new EventLevel("conversation", 0);

    private final ChannelExecutionStrategy.Conversation<T> strategy;

    ConversationChannelAdapter(ChannelExecutionStrategy.Conversation<T> strategy) {
        this.strategy = strategy;
    }

    Uni<ExecutionResult> execute(ChannelBinding binding,
                                 ExecutionModel<T> model,
                                 T initialContext,
                                 MessageDispatcher dispatcher) {
        return Uni.createFrom().item(() -> {
            var participants = model.candidateSupplier().get().stream()
                    .map(c -> strategy.resolvedParticipantMapper().apply(c.ref()))
                    .toList();

            var projection = createProjection();
            var turnPolicy = resolveTurnPolicy(model);
            var observationService = createObservationService();

            Consumer<MessageView> responseDispatcher = responseMessage ->
                    dispatcher.dispatch(MessageDispatch.builder()
                            .channelId(binding.channelId())
                            .sender(responseMessage.sender())
                            .type(responseMessage.type() != null ? responseMessage.type() : MessageType.STATUS)
                            .content(responseMessage.content())
                            .actorType(ActorType.AGENT)
                            .build());

            var triggeringMessage = strategy.config().triggerMapper().apply(initialContext);

            var invoker = strategy.config().conversationInvoker() != null
                    ? strategy.config().conversationInvoker()
                    : AgentInvoker.<String>defaultInvoker();

            var orchestrator = new ConversationOrchestrator(
                    projection, observationService, turnPolicy,
                    strategy.conversationTermination(),
                    invoker,
                    strategy.config().promptAssembler(),
                    strategy.config().responseBuilder(),
                    responseDispatcher,
                    participants);

            var outcome = orchestrator.converse(triggeringMessage).await().indefinitely();

            return (ExecutionResult) switch (outcome.terminationDecision()) {
                case TerminationDecision.Complete c -> new ExecutionResult.Completed(outcome.finalState());
                case TerminationDecision.Failed f -> new ExecutionResult.Failed(f.reason(), null);
                case TerminationDecision.Escalate e -> new ExecutionResult.Escalated(e.reason());
                case TerminationDecision.Continue ignored -> new ExecutionResult.Cancelled();
            };
        });
    }

    private ConversationProjection createProjection() {
        if (strategy.config().projectionFactory() != null) {
            return strategy.config().projectionFactory().get();
        }
        return new DefaultConversationProjection();
    }

    private TurnPolicy resolveTurnPolicy(ExecutionModel<T> model) {
        if (strategy.config().turnPolicyOverride() != null) {
            return strategy.config().turnPolicyOverride();
        }
        if (model.patternType() == PatternType.DEBATE) {
            return new RoundRobinTurnPolicy();
        }
        if (model.patternType() == PatternType.VOTING) {
            return new FreeTurnPolicy();
        }
        return new PointAddressedTurnPolicy();
    }

    private PartitionedObservationService<MessageView, String> createObservationService() {
        return new PartitionedObservationService<>(
                (events, ctx) -> CompletableFuture.completedFuture(
                        new ObservationResult("", List.of(),
                                events.size(), 0L, null)),
                event -> Map.of(),
                msg -> msg.createdAt() != null ? msg.createdAt().toEpochMilli() : 0L,
                CONVERSATION);
    }
}
