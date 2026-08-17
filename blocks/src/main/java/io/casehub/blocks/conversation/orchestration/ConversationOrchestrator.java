package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationProjection;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.summarisation.observation.PartitionedObservationService;
import io.casehub.qhorus.api.message.MessageView;
import io.smallrye.mutiny.Uni;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ConversationOrchestrator {

    private static final System.Logger LOG =
            System.getLogger(ConversationOrchestrator.class.getName());

    private final ConversationProjection projection;
    private final PartitionedObservationService<MessageView, String> observationService;
    private final TurnPolicy turnPolicy;
    private final TerminationCondition<ConversationState> terminationCondition;
    private final AgentInvoker<String> agentInvoker;
    private final PromptAssembler promptAssembler;
    private final ResponseMessageBuilder responseBuilder;
    private final Consumer<MessageView> responseDispatcher;
    private final List<AgentParticipant> participants;
    private final @Nullable ConversationListener listener;
    private volatile boolean terminated = false;

    public ConversationOrchestrator(
            ConversationProjection projection,
            PartitionedObservationService<MessageView, String> observationService,
            TurnPolicy turnPolicy,
            TerminationCondition<ConversationState> terminationCondition,
            AgentInvoker<String> agentInvoker,
            PromptAssembler promptAssembler,
            ResponseMessageBuilder responseBuilder,
            Consumer<MessageView> responseDispatcher,
            List<AgentParticipant> participants) {
        this(projection, observationService, turnPolicy, terminationCondition,
                agentInvoker, promptAssembler, responseBuilder, responseDispatcher,
                participants, null);
    }

    public ConversationOrchestrator(
            ConversationProjection projection,
            PartitionedObservationService<MessageView, String> observationService,
            TurnPolicy turnPolicy,
            TerminationCondition<ConversationState> terminationCondition,
            AgentInvoker<String> agentInvoker,
            PromptAssembler promptAssembler,
            ResponseMessageBuilder responseBuilder,
            Consumer<MessageView> responseDispatcher,
            List<AgentParticipant> participants,
            @Nullable ConversationListener listener) {
        this.projection = projection;
        this.observationService = observationService;
        this.turnPolicy = turnPolicy;
        this.terminationCondition = terminationCondition;
        this.agentInvoker = agentInvoker;
        this.promptAssembler = promptAssembler;
        this.responseBuilder = responseBuilder;
        this.responseDispatcher = responseDispatcher;
        this.participants = List.copyOf(participants);
        this.listener = listener;

        for (var p : this.participants) {
            observationService.addObserver(p.agentId(), p.agentId());
        }
    }

    public Uni<ConversationOutcome> converse(MessageView triggeringMessage) {
        return Uni.createFrom().item(() -> {
            terminated = false;
            var start = Instant.now();
            var state = projection.identity();
            var allResults = new ArrayList<AgentResult>();
            var queue = new ArrayDeque<MessageView>();
            int dispatchCount = 0;

            queue.add(triggeringMessage);

            TerminationDecision finalDecision = TerminationDecision.Continue.INSTANCE;

            while (!queue.isEmpty() && !terminated) {
                var message = queue.poll();

                state = projection.apply(state, message);
                observationService.publishEvent(message);

                var turnContext = extractContext(message);
                var responders = turnPolicy.nextResponders(
                        state, turnContext, participants);

                for (var agent : responders) {
                    if (terminated) break;

                    AgentResult result;
                    try {
                        var drain = observationService.drain(
                                agent.agentId(), agent.agentId(),
                                System.currentTimeMillis());
                        String prompt;
                        try {
                            prompt = promptAssembler.assemble(agent, drain, state);
                        } catch (Exception e) {
                            LOG.log(System.Logger.Level.WARNING,
                                    "Prompt assembly failed for " + agent.agentId(), e);
                            result = AgentResult.failure(agent.agentRef(), e.getMessage());
                            allResults.add(result);
                            dispatchCount++;
                            continue;
                        }
                        result = agentInvoker.invoke(agent.agentRef(), prompt)
                                .await().indefinitely();
                    } catch (Exception e) {
                        LOG.log(System.Logger.Level.WARNING,
                                "Agent invocation failed: " + agent.agentId(), e);
                        result = AgentResult.failure(agent.agentRef(), e.getMessage());
                    }

                    allResults.add(result);
                    dispatchCount++;

                    if (result.status() == AgentResult.AgentResultStatus.FAILURE
                            || result.status() == AgentResult.AgentResultStatus.TIMEOUT) {
                        continue;
                    }

                    var responseMessage = responseBuilder.build(agent, result, state);
                    state = projection.apply(state, responseMessage);
                    observationService.publishEvent(responseMessage);
                    responseDispatcher.accept(responseMessage);
                    queue.add(responseMessage);

                    var elapsed = Duration.between(start, Instant.now());
                    var termCtx = new TerminationContext<>(
                            state, dispatchCount, elapsed,
                            List.copyOf(allResults));
                    finalDecision = terminationCondition.evaluate(termCtx);

                    if (listener != null) {
                        listener.onDispatch(state, finalDecision,
                                dispatchCount, elapsed);
                    }

                    if (!(finalDecision instanceof TerminationDecision.Continue)) {
                        break;
                    }
                }

                if (!(finalDecision instanceof TerminationDecision.Continue)) {
                    break;
                }
            }

            if (finalDecision instanceof TerminationDecision.Continue) {
                finalDecision = new TerminationDecision.Complete("Queue drained");
            }

            return new ConversationOutcome(
                    state, finalDecision, allResults, dispatchCount,
                    Duration.between(start, Instant.now()));
        });
    }

    public void terminate() {
        terminated = true;
    }

    private TurnContext extractContext(MessageView message) {
        var sender = message.sender() != null ? message.sender() : "";
        return new TurnContext(sender, null, "", Map.of());
    }
}
