package io.casehub.blocks.agentic.judgment;

import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallerStrategyTest {

    @Test
    void callerRef_agentFactory() {
        var agent = AgentRef.external("judge", ctx -> null);
        var ref = CallerRef.agent("judge-1", agent);
        assertThat(ref.id()).isEqualTo("judge-1");
        assertThat(ref.agentRef()).isEqualTo(agent);
        assertThat(ref.routingHints()).isNull();
    }

    @Test
    void callerRef_humanFactory() {
        var ref = CallerRef.human("approver", Map.of("candidateGroups", "managers"));
        assertThat(ref.id()).isEqualTo("approver");
        assertThat(ref.agentRef()).isNull();
        assertThat(ref.routingHints()).containsEntry("candidateGroups", "managers");
    }

    @Test
    void single_strategy() {
        var ref = CallerRef.agent("j", null);
        var strategy = CallerStrategy.single(ref);
        assertThat(strategy).isInstanceOf(CallerStrategy.Single.class);
        assertThat(((CallerStrategy.Single) strategy).caller()).isEqualTo(ref);
    }

    @Test
    void single_default() {
        var strategy = CallerStrategy.single();
        assertThat(strategy).isInstanceOf(CallerStrategy.Single.class);
        assertThat(((CallerStrategy.Single) strategy).caller().id()).isEqualTo("default");
    }

    @Test
    void fanOut_strategy_copiesList() {
        var refs = new java.util.ArrayList<>(List.of(
                CallerRef.agent("a", null), CallerRef.agent("b", null)));
        var strategy = CallerStrategy.fanOut(refs, responses -> null);
        refs.clear();
        assertThat(((CallerStrategy.FanOut) strategy).callers()).hasSize(2);
    }

    @Test
    void escalationChain_strategy() {
        var refs = List.of(CallerRef.agent("a", null), CallerRef.agent("b", null));
        var strategy = new CallerStrategy.EscalationChain(refs);
        assertThat(strategy.callers()).hasSize(2);
    }

    @Test
    void retryPolicy_defaults() {
        var policy = RetryPolicy.defaults();
        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.exhaustionPolicy()).isEqualTo(ExhaustionPolicy.FAIL);
    }

    @Test
    void verifierFailurePolicy_ordering() {
        assertThat(VerifierFailurePolicy.RETRY_WITH_FEEDBACK.compareTo(VerifierFailurePolicy.ESCALATE)).isLessThan(0);
        assertThat(VerifierFailurePolicy.ESCALATE.compareTo(VerifierFailurePolicy.FAIL)).isLessThan(0);
    }
}
