package io.casehub.blocks.agentic;

import java.time.Duration;

public record FailurePolicy(
        RoutingFailureAction onRoutingFailure,
        AggregationFailureAction onDeadlock,
        AgentRetryPolicy agentRetry
) {
    public enum RoutingFailureAction { FAIL, RETRY_BROADER, ESCALATE }
    public enum AggregationFailureAction { FAIL, ESCALATE, RETRY_DIFFERENT }
    public enum AgentFailureAction { FAIL, ESCALATE, SKIP }

    public record AgentRetryPolicy(int maxRetries, Duration backoff,
                                   AgentFailureAction onExhausted) {}

    public static FailurePolicy defaults() {
        return new FailurePolicy(
                RoutingFailureAction.FAIL,
                AggregationFailureAction.FAIL,
                new AgentRetryPolicy(3, Duration.ofSeconds(1), AgentFailureAction.FAIL));
    }
}
