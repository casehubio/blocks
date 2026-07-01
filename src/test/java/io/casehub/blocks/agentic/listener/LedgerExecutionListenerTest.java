package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerExecutionListenerTest {

    record LedgerRecord(OrchestrationEventType type, String actorId,
                        String actorRole, Map<String, Object> data) {}

    @Test
    void carriesActorIdentityOnEveryEntry() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        listener.onRoutingDecision(
                new RoutingDecision.Selected(List.of(agent), "reason"), List.of());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).actorId()).isEqualTo("supervisor-123");
        assertThat(records.get(0).actorRole()).isEqualTo("orchestration-router");
    }

    @Test
    void recordsEscalationReasonOnCompletion() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        listener.onExecutionComplete(
                new io.casehub.blocks.agentic.model.ExecutionResult.Escalated("human needed"));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).data()).containsEntry("reason", "human needed");
    }

    @Test
    void recordsAgentResultWithOutputSummary() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var result = new AgentResult(agent, "analysis done",
                Duration.ofMillis(100), AgentResult.AgentResultStatus.SUCCESS);
        listener.onAgentResult(result);

        assertThat(records.get(0).data()).containsEntry("outputType", "String");
    }

    @Test
    void recordsFailedResultReasonOnCompletion() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        listener.onExecutionComplete(
                new io.casehub.blocks.agentic.model.ExecutionResult.Failed("timeout", new RuntimeException()));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).data())
                .containsEntry("result", "Failed")
                .containsEntry("reason", "timeout");
    }

    @Test
    void usesOrchestratorSupervisorRoleForNonRoutingEvents() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-456");

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));

        listener.onActivation(agent, true);
        listener.onAgentDispatched(agent);
        listener.onFailure(agent, new RuntimeException("err"));
        listener.onTermination(new io.casehub.blocks.agentic.termination.TerminationDecision.Continue());
        listener.onExecutionStart(io.casehub.blocks.agentic.pattern.Patterns.<String>supervisor()
                .agents(agent).task("test").build());

        assertThat(records).allSatisfy(r -> {
            assertThat(r.actorId()).isEqualTo("supervisor-456");
            assertThat(r.actorRole()).isEqualTo("orchestration-supervisor");
        });
    }

    @Test
    void truncatesLongOutputSummary() {
        var records = new ArrayList<LedgerRecord>();
        var listener = new LedgerExecutionListener(
                (type, actorId, role, data) ->
                        records.add(new LedgerRecord(type, actorId, role, data)),
                "supervisor-123");

        var agent = AgentRef.external(x ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var longOutput = "y".repeat(600);
        var result = new AgentResult(agent, longOutput, Duration.ofMillis(100),
                AgentResult.AgentResultStatus.SUCCESS);

        listener.onAgentResult(result);

        var summary = (String) records.get(0).data().get("outputSummary");
        assertThat(summary).hasSize(503); // 500 + "..."
        assertThat(summary).endsWith("...");
    }
}
