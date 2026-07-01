package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;

import java.util.List;

public interface ExecutionEventListener {
    default void onRoutingDecision(RoutingDecision decision,
                                   List<RoutingCandidate> candidates) {}
    default void onActivation(AgentRef agent, boolean activated) {}
    default void onAgentDispatched(AgentRef agent) {}
    default void onAgentResult(AgentResult result) {}
    default void onAggregation(AggregationResult result) {}
    default void onTermination(TerminationDecision decision) {}
    default void onStateTransition(ExecutionState from, ExecutionState to) {}
    default void onFailure(AgentRef agent, Throwable cause) {}
    default void onExecutionStart(ExecutionModel<?> model) {}
    default void onExecutionComplete(ExecutionResult result) {}
}
