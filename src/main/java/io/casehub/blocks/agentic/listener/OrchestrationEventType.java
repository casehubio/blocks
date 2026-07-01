package io.casehub.blocks.agentic.listener;

/**
 * Orchestration-level event types produced by blocks' execution drivers.
 * Maps to {@code CaseHubEventType} at the EventLog sink — the mapping is owned
 * by the sink implementation, not by blocks. When engine#626 adds orchestration
 * types to {@code CaseHubEventType}, the sink maps directly.
 */
public enum OrchestrationEventType {
    EXECUTION_STARTED,
    ROUTING_DECISION,
    ACTIVATION_EVALUATED,
    AGENT_DISPATCHED,
    AGENT_RESULT,
    AGENT_FAILED,
    AGGREGATION_COMPLETED,
    TERMINATION_EVALUATED,
    EXECUTION_COMPLETED
}
