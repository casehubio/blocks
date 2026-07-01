package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persists compliance-grade audit entries via a consumer-provided sink.
 * Each entry carries actor identity and role for EU AI Act Art.12 regulatory traceability.
 *
 * <p>Records the full decision-dispatch-result chain: routing decisions with LLM reasoning,
 * activation evaluations, agent dispatches and results, termination decisions,
 * escalation events, and failures. This chain closes the Art.12 audit gap — you know
 * what was decided (routing), what was activated, what was dispatched, what each agent
 * returned, and why execution terminated.
 *
 * <p>Sink implementations must be non-blocking — use in-memory buffers or async
 * queues. The sink is called synchronously from the driver loop; blocking I/O
 * in the sink stalls the execution.
 */
public class LedgerExecutionListener implements ExecutionEventListener {

    @FunctionalInterface
    public interface LedgerSink {
        void record(OrchestrationEventType type, String actorId, String actorRole,
                    Map<String, Object> data);
    }

    private final LedgerSink sink;
    private final String supervisorActorId;

    public LedgerExecutionListener(LedgerSink sink, String supervisorActorId) {
        this.sink = Objects.requireNonNull(sink);
        this.supervisorActorId = Objects.requireNonNull(supervisorActorId);
    }

    @Override
    public void onRoutingDecision(RoutingDecision decision, List<RoutingCandidate> candidates) {
        var data = new LinkedHashMap<String, Object>();
        data.put("decision", decision.getClass().getSimpleName());
        data.put("candidateCount", candidates.size());
        if (decision instanceof RoutingDecision.Selected s) {
            data.put("selectedAgents", s.agents().size());
            if (s.reason() != null) data.put("reason", s.reason());
        } else if (decision instanceof RoutingDecision.Unresolvable u) {
            data.put("reason", u.reason());
        } else if (decision instanceof RoutingDecision.Escalate e) {
            data.put("reason", e.reason());
        }
        sink.record(OrchestrationEventType.ROUTING_DECISION,
                supervisorActorId, "orchestration-router", data);
    }

    @Override
    public void onActivation(AgentRef agent, boolean activated) {
        sink.record(OrchestrationEventType.ACTIVATION_EVALUATED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("agent", ExecutionEventListener.agentName(agent),
                       "activated", activated));
    }

    @Override
    public void onAgentDispatched(AgentRef agent) {
        sink.record(OrchestrationEventType.AGENT_DISPATCHED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("agent", ExecutionEventListener.agentName(agent)));
    }

    @Override
    public void onAgentResult(AgentResult result) {
        var data = new LinkedHashMap<String, Object>();
        data.put("agent", ExecutionEventListener.agentName(result.agent()));
        data.put("status", result.status().name());
        data.put("durationMs", result.duration().toMillis());
        if (result.output() != null) {
            data.put("outputType", result.output().getClass().getSimpleName());
            data.put("outputSummary", truncate(result.output().toString(), 500));
        }
        sink.record(OrchestrationEventType.AGENT_RESULT,
                supervisorActorId, "orchestration-supervisor", data);
    }

    @Override
    public void onFailure(AgentRef agent, Throwable cause) {
        sink.record(OrchestrationEventType.AGENT_FAILED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("agent", ExecutionEventListener.agentName(agent),
                       "error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName()));
    }

    @Override
    public void onTermination(TerminationDecision decision) {
        var data = new LinkedHashMap<String, Object>();
        data.put("decision", decision.getClass().getSimpleName());
        if (decision instanceof TerminationDecision.Failed f) data.put("reason", f.reason());
        if (decision instanceof TerminationDecision.Escalate e) data.put("reason", e.reason());
        sink.record(OrchestrationEventType.TERMINATION_EVALUATED,
                supervisorActorId, "orchestration-supervisor", data);
    }

    @Override
    public void onExecutionStart(ExecutionModel<?> model) {
        sink.record(OrchestrationEventType.EXECUTION_STARTED,
                supervisorActorId, "orchestration-supervisor",
                Map.of("task", model.task()));
    }

    @Override
    public void onExecutionComplete(ExecutionResult result) {
        var data = new LinkedHashMap<String, Object>();
        data.put("result", result.getClass().getSimpleName());
        if (result instanceof ExecutionResult.Escalated e) data.put("reason", e.reason());
        if (result instanceof ExecutionResult.Failed f) data.put("reason", f.reason());
        sink.record(OrchestrationEventType.EXECUTION_COMPLETED,
                supervisorActorId, "orchestration-supervisor", data);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
