package io.casehub.blocks.agentic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.casehub.blocks.agentic.FailurePolicy.*;
import static org.assertj.core.api.Assertions.assertThat;

class FailurePolicyTest {

    @Nested
    class Defaults {
        @Test
        void defaultPolicyUsesFail() {
            var policy = FailurePolicy.defaults();
            assertThat(policy.onRoutingFailure()).isEqualTo(RoutingFailureAction.FAIL);
            assertThat(policy.onDeadlock()).isEqualTo(AggregationFailureAction.FAIL);
            assertThat(policy.agentRetry().maxRetries()).isEqualTo(3);
            assertThat(policy.agentRetry().backoff()).isEqualTo(Duration.ofSeconds(1));
            assertThat(policy.agentRetry().onExhausted()).isEqualTo(AgentFailureAction.FAIL);
        }
    }

    @Test
    void allRoutingFailureActionsExist() {
        assertThat(RoutingFailureAction.values()).containsExactly(
                RoutingFailureAction.FAIL,
                RoutingFailureAction.RETRY_BROADER,
                RoutingFailureAction.ESCALATE);
    }

    @Test
    void allAggregationFailureActionsExist() {
        assertThat(AggregationFailureAction.values()).containsExactly(
                AggregationFailureAction.FAIL,
                AggregationFailureAction.ESCALATE,
                AggregationFailureAction.RETRY_DIFFERENT);
    }

    @Test
    void allAgentFailureActionsExist() {
        assertThat(AgentFailureAction.values()).containsExactly(
                AgentFailureAction.FAIL,
                AgentFailureAction.ESCALATE,
                AgentFailureAction.SKIP);
    }

    @Test
    void defaultsUsesFixedBackoff() {
        var policy = FailurePolicy.defaults();
        assertThat(policy.agentRetry().backoffStrategy())
                .isEqualTo(BackoffStrategy.FIXED);
    }

    @Test
    void agentRetryPolicyCarriesAllFourFields() {
        var retry = new AgentRetryPolicy(
                5, Duration.ofMillis(200),
                BackoffStrategy.EXPONENTIAL,
                AgentFailureAction.ESCALATE);

        assertThat(retry.maxRetries()).isEqualTo(5);
        assertThat(retry.backoff()).isEqualTo(Duration.ofMillis(200));
        assertThat(retry.backoffStrategy()).isEqualTo(BackoffStrategy.EXPONENTIAL);
        assertThat(retry.onExhausted()).isEqualTo(AgentFailureAction.ESCALATE);
    }
}
