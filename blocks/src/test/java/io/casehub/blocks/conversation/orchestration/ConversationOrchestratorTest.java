package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.blocks.conversation.ConversationProjection;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.observation.ObservationResult;
import io.casehub.blocks.summarisation.observation.PartitionedObservationService;
import io.casehub.qhorus.api.message.MessageView;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationOrchestratorTest {

    record DispatchEvent(ConversationState state, TerminationDecision decision,
                         int dispatchCount, Duration elapsed) {}

    static class TestProjection extends ConversationProjection {
        @Override protected String sentinel() { return "TEST:"; }
        @Override protected boolean isPointInitiator(String entryType) {
            return "RAISE".equals(entryType);
        }
        @Override protected String statusAfter(String entryType) {
            return switch (entryType) {
                case "AGREE" -> "AGREED";
                case "COUNTER" -> "ACTIVE";
                case "DISPUTE" -> "DISPUTED";
                default -> null;
            };
        }
    }

    private final TestProjection projection = new TestProjection();
    private final EventLevel CONVERSATION = new EventLevel("conversation", 0);

    private MessageView mockMessage(String sender, String content, long id) {
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn(sender);
        when(msg.content()).thenReturn(content);
        when(msg.correlationId()).thenReturn("corr-" + id);
        when(msg.id()).thenReturn(id);
        when(msg.type()).thenReturn(null);
        when(msg.createdAt()).thenReturn(Instant.now());
        when(msg.topic()).thenReturn("general");
        return msg;
    }

    private PartitionedObservationService<MessageView, String> createObservationService() {
        return new PartitionedObservationService<>(
                (events, ctx) -> CompletableFuture.completedFuture(
                        new ObservationResult("observed", List.of(),
                                events.size(), 0L, null)),
                event -> Map.of(),
                msg -> msg.createdAt() != null ? msg.createdAt().toEpochMilli() : 0L,
                CONVERSATION);
    }

    @SuppressWarnings("unchecked")
    private TerminationCondition<ConversationState> maxIterations(int n) {
        return (TerminationCondition<ConversationState>)
                (TerminationCondition<?>) new MaxIterationsTermination<>(n);
    }

    @Test
    void twoAgentDebate_roundRobin_maxIterationsStops() {
        var observationService = createObservationService();
        var dispatched = new ArrayList<MessageView>();
        var callCount = new AtomicInteger();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            int n = callCount.incrementAndGet();
            return Uni.createFrom().item(AgentResult.success(agent, "Response " + n));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "Review prompt");
        var bob = new AgentParticipant(
                AgentRef.external("bob", i -> null), "IMP", "Implement prompt");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\n" + result.output(), callCount.get() + 100);

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new RoundRobinTurnPolicy(),
                maxIterations(4),
                invoker,
                (agent, drain, state) -> agent.systemPrompt(),
                responseBuilder,
                dispatched::add,
                List.of(alice, bob)
        );

        var trigger = mockMessage("human", "Start the debate", 0);
        var outcome = orchestrator.converse(trigger).await().indefinitely();

        assertThat(outcome.dispatchCount()).isEqualTo(4);
        assertThat(outcome.terminationDecision())
                .isInstanceOf(TerminationDecision.Complete.class);
        assertThat(dispatched).hasSize(4);
    }

    @Test
    void terminationDecision_complete_stopsLoop() {
        var observationService = createObservationService();
        var dispatched = new ArrayList<MessageView>();
        var callCount = new AtomicInteger();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            int n = callCount.incrementAndGet();
            return Uni.createFrom().item(AgentResult.success(agent, "Response " + n));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");
        var bob = new AgentParticipant(
                AgentRef.external("bob", i -> null), "IMP", "");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\n" + result.output(), callCount.get() + 100);

        TerminationCondition<ConversationState> stopAfterTwo = ctx -> {
            if (ctx.iterationCount() >= 2) {
                return new TerminationDecision.Complete("Consensus reached");
            }
            return TerminationDecision.Continue.INSTANCE;
        };

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new RoundRobinTurnPolicy(),
                stopAfterTwo,
                invoker,
                (agent, drain, state) -> "",
                responseBuilder,
                dispatched::add,
                List.of(alice, bob)
        );

        var trigger = mockMessage("human", "Start", 0);
        var outcome = orchestrator.converse(trigger).await().indefinitely();

        assertThat(outcome.terminationDecision())
                .isInstanceOf(TerminationDecision.Complete.class);
        assertThat(((TerminationDecision.Complete) outcome.terminationDecision()).result())
                .isEqualTo("Consensus reached");
        assertThat(outcome.dispatchCount()).isEqualTo(2);
    }

    @Test
    void agentFailure_skipsAndContinues() {
        var observationService = createObservationService();
        var dispatched = new ArrayList<MessageView>();
        var callCount = new AtomicInteger();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            int n = callCount.incrementAndGet();
            if (agent.name().equals("alice")) {
                return Uni.createFrom().item(AgentResult.failure(agent, "LLM error"));
            }
            return Uni.createFrom().item(AgentResult.success(agent, "Bob response " + n));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");
        var bob = new AgentParticipant(
                AgentRef.external("bob", i -> null), "IMP", "");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\n" + result.output(), callCount.get() + 100);

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new FreeTurnPolicy(),
                maxIterations(3),
                invoker,
                (agent, drain, state) -> "",
                responseBuilder,
                dispatched::add,
                List.of(alice, bob)
        );

        var trigger = mockMessage("human", "Start", 0);
        var outcome = orchestrator.converse(trigger).await().indefinitely();

        assertThat(outcome.agentResults().stream()
                .filter(r -> r.status() == AgentResult.AgentResultStatus.FAILURE)
                .count()).isGreaterThanOrEqualTo(1);
        assertThat(outcome.agentResults().stream()
                .filter(r -> r.status() == AgentResult.AgentResultStatus.SUCCESS)
                .count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void emptyResponders_queueDrains() {
        var observationService = createObservationService();
        var dispatched = new ArrayList<MessageView>();

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new AddressedTurnPolicy(),
                maxIterations(10),
                (agent, prompt) -> Uni.createFrom().item(AgentResult.success(agent, "ok")),
                (agent, drain, state) -> "",
                (agent, result, state) -> mockMessage(agent.agentId(), "resp", 999),
                dispatched::add,
                List.of(alice)
        );

        var trigger = mockMessage("human", "Hello", 0);
        var outcome = orchestrator.converse(trigger).await().indefinitely();

        assertThat(outcome.dispatchCount()).isEqualTo(0);
        assertThat(outcome.terminationDecision())
                .isInstanceOf(TerminationDecision.Complete.class);
        assertThat(((TerminationDecision.Complete) outcome.terminationDecision()).result())
                .isEqualTo("Queue drained");
    }

    @Test
    void responseDispatcher_calledPerResponse() {
        var observationService = createObservationService();
        var dispatched = new ArrayList<MessageView>();
        var callCount = new AtomicInteger();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            callCount.incrementAndGet();
            return Uni.createFrom().item(AgentResult.success(agent, "resp"));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");
        var bob = new AgentParticipant(
                AgentRef.external("bob", i -> null), "IMP", "");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp", callCount.get() + 100);

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new RoundRobinTurnPolicy(),
                maxIterations(3),
                invoker,
                (agent, drain, state) -> "",
                responseBuilder,
                dispatched::add,
                List.of(alice, bob)
        );

        var trigger = mockMessage("human", "Start", 0);
        orchestrator.converse(trigger).await().indefinitely();

        assertThat(dispatched).hasSize(3);
    }

    @Test
    void listener_receivesAllDispatches() {
        var observationService = createObservationService();
        var dispatched = new ArrayList<MessageView>();
        var callCount = new AtomicInteger();
        var events = new ArrayList<DispatchEvent>();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            callCount.incrementAndGet();
            return Uni.createFrom().item(AgentResult.success(agent, "resp"));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");
        var bob = new AgentParticipant(
                AgentRef.external("bob", i -> null), "IMP", "");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                        callCount.get() + 100);

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new RoundRobinTurnPolicy(),
                maxIterations(4),
                invoker,
                (agent, drain, state) -> "",
                responseBuilder,
                dispatched::add,
                List.of(alice, bob),
                (state, decision, count, elapsed) ->
                        events.add(new DispatchEvent(state, decision, count, elapsed))
        );

        var trigger = mockMessage("human", "Start", 0);
        orchestrator.converse(trigger).await().indefinitely();

        assertThat(events).hasSize(4);
        assertThat(events.stream().map(DispatchEvent::dispatchCount).toList())
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void listener_receivesFinalTerminationDecision() {
        var observationService = createObservationService();
        var callCount = new AtomicInteger();
        var events = new ArrayList<DispatchEvent>();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            callCount.incrementAndGet();
            return Uni.createFrom().item(AgentResult.success(agent, "resp"));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                        callCount.get() + 100);

        TerminationCondition<ConversationState> stopAfterTwo = ctx -> {
            if (ctx.iterationCount() >= 2) {
                return new TerminationDecision.Complete("Done");
            }
            return TerminationDecision.Continue.INSTANCE;
        };

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new RoundRobinTurnPolicy(),
                stopAfterTwo,
                invoker,
                (agent, drain, state) -> "",
                responseBuilder,
                msg -> {},
                List.of(alice),
                (state, decision, count, elapsed) ->
                        events.add(new DispatchEvent(state, decision, count, elapsed))
        );

        var trigger = mockMessage("human", "Start", 0);
        orchestrator.converse(trigger).await().indefinitely();

        assertThat(events).isNotEmpty();
        var lastEvent = events.getLast();
        assertThat(lastEvent.decision())
                .isInstanceOf(TerminationDecision.Complete.class);
        assertThat(((TerminationDecision.Complete) lastEvent.decision()).result())
                .isEqualTo("Done");
    }

    @Test
    void listener_notCalledOnAgentFailure() {
        var observationService = createObservationService();
        var events = new ArrayList<DispatchEvent>();

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");

        AgentInvoker<String> invoker = (agent, prompt) ->
                Uni.createFrom().item(AgentResult.failure(agent, "LLM error"));

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new FreeTurnPolicy(),
                maxIterations(10),
                invoker,
                (agent, drain, state) -> "",
                (agent, result, state) -> mockMessage(agent.agentId(), "resp", 999),
                msg -> {},
                List.of(alice),
                (state, decision, count, elapsed) ->
                        events.add(new DispatchEvent(state, decision, count, elapsed))
        );

        var trigger = mockMessage("human", "Start", 0);
        var outcome = orchestrator.converse(trigger).await().indefinitely();

        assertThat(outcome.dispatchCount()).isEqualTo(1);
        assertThat(events).isEmpty();
    }

    @Test
    void nullListener_noNPE() {
        var observationService = createObservationService();
        var dispatched = new ArrayList<MessageView>();
        var callCount = new AtomicInteger();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            callCount.incrementAndGet();
            return Uni.createFrom().item(AgentResult.success(agent, "resp"));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                        callCount.get() + 100);

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new RoundRobinTurnPolicy(),
                maxIterations(2),
                invoker,
                (agent, drain, state) -> "",
                responseBuilder,
                dispatched::add,
                List.of(alice)
        );

        var trigger = mockMessage("human", "Start", 0);
        var outcome = orchestrator.converse(trigger).await().indefinitely();

        assertThat(outcome.dispatchCount()).isEqualTo(2);
    }

    @Test
    void listener_elapsedMonotonicallyIncreasing() {
        var observationService = createObservationService();
        var callCount = new AtomicInteger();
        var events = new ArrayList<DispatchEvent>();

        AgentInvoker<String> invoker = (agent, prompt) -> {
            callCount.incrementAndGet();
            return Uni.createFrom().item(AgentResult.success(agent, "resp"));
        };

        var alice = new AgentParticipant(
                AgentRef.external("alice", i -> null), "REV", "");

        ResponseMessageBuilder responseBuilder = (agent, result, state) ->
                mockMessage(agent.agentId(), "TEST:entry_type=COMMENT\nresp",
                        callCount.get() + 100);

        var orchestrator = new ConversationOrchestrator(
                projection, observationService,
                new RoundRobinTurnPolicy(),
                maxIterations(3),
                invoker,
                (agent, drain, state) -> "",
                responseBuilder,
                msg -> {},
                List.of(alice),
                (state, decision, count, elapsed) ->
                        events.add(new DispatchEvent(state, decision, count, elapsed))
        );

        var trigger = mockMessage("human", "Start", 0);
        orchestrator.converse(trigger).await().indefinitely();

        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        for (int i = 1; i < events.size(); i++) {
            assertThat(events.get(i).elapsed())
                    .isGreaterThanOrEqualTo(events.get(i - 1).elapsed());
        }
    }
}
