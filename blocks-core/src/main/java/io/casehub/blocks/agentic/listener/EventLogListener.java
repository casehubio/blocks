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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persists orchestration events to the engine's EventLog via a consumer-provided sink.
 * Records all events for operational observability.
 *
 * <p>Sink implementations must be non-blocking — use in-memory buffers or async
 * queues. The sink is called synchronously from the driver loop; blocking I/O
 * in the sink stalls the execution.
 */
public class EventLogListener implements ExecutionEventListener {

    @FunctionalInterface
    public interface EventSink {
        void record(OrchestrationEventType type, Map<String, Object> payload);
    }

    private final EventSink sink;

    public EventLogListener(EventSink sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    @Override
    public void onRoutingDecision(RoutingDecision decision, List<RoutingCandidate> candidates) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("decision", decision.getClass().getSimpleName());
        payload.put("candidateCount", candidates.size());
        if (decision instanceof RoutingDecision.Selected s) {
            payload.put("selectedAgents", s.agents().size());
            if (s.reason() != null) payload.put("reason", s.reason());
        } else if (decision instanceof RoutingDecision.Unresolvable u) {
            payload.put("reason", u.reason());
        } else if (decision instanceof RoutingDecision.Escalate e) {
            payload.put("reason", e.reason());
        }
        sink.record(OrchestrationEventType.ROUTING_DECISION, payload);
    }

    @Override
    public void onActivation(AgentRef agent, boolean activated) {
        sink.record(OrchestrationEventType.ACTIVATION_EVALUATED, Map.of(
                "agent", ExecutionEventListener.agentName(agent),
                "activated", activated));
    }

    @Override
    public void onAgentDispatched(AgentRef agent) {
        sink.record(OrchestrationEventType.AGENT_DISPATCHED,
                Map.of("agent", ExecutionEventListener.agentName(agent)));
    }

    @Override
    public void onAgentResult(AgentResult result) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agent", ExecutionEventListener.agentName(result.agent()));
        payload.put("status", result.status().name());
        payload.put("durationMs", result.duration().toMillis());
        if (result.output() != null) {
            payload.put("outputType", result.output().getClass().getSimpleName());
            payload.put("outputSummary", truncate(result.output().toString(), 500));
        }
        sink.record(OrchestrationEventType.AGENT_RESULT, payload);
    }

    @Override
    public void onFailure(AgentRef agent, Throwable cause) {
        sink.record(OrchestrationEventType.AGENT_FAILED, Map.of(
                "agent", ExecutionEventListener.agentName(agent),
                "error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName()));
    }

    @Override
    public void onAggregation(AggregationResult result) {
        sink.record(OrchestrationEventType.AGGREGATION_COMPLETED, Map.of(
                "result", result.getClass().getSimpleName()));
    }

    @Override
    public void onTermination(TerminationDecision decision) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("decision", decision.getClass().getSimpleName());
        if (decision instanceof TerminationDecision.Failed f) payload.put("reason", f.reason());
        if (decision instanceof TerminationDecision.Escalate e) payload.put("reason", e.reason());
        sink.record(OrchestrationEventType.TERMINATION_EVALUATED, payload);
    }

    @Override
    public void onExecutionStart(ExecutionModel<?> model) {
        sink.record(OrchestrationEventType.EXECUTION_STARTED, Map.of("task", model.task()));
    }

    @Override
    public void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                    int iterationCount) {
        sink.record(OrchestrationEventType.EXECUTION_COMPLETED, Map.of(
                "result", result.getClass().getSimpleName(),
                "durationMs", executionDuration.toMillis(),
                "iterations", iterationCount));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
