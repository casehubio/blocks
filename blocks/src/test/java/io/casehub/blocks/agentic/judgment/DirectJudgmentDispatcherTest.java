package io.casehub.blocks.agentic.judgment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectJudgmentDispatcherTest {

    private static JudgmentContext<String> ctx() {
        return new JudgmentContext<>("state", List.of(), null, 0, null);
    }

    @Test
    void dispatchesViaAgentInvokerAndMapsStringResult() {
        var agent = AgentRef.external("judge", ctx ->
                CompletableFuture.completedFuture(
                        new AgentResult(null, "APPROVE: looks good", Duration.ZERO,
                                AgentResult.AgentResultStatus.SUCCESS)));
        var caller = CallerRef.agent("judge-1", agent);
        var dispatcher = new DirectJudgmentDispatcher();
        var request = new JudgmentDispatchRequest(ctx(), caller, null);
        var response = dispatcher.dispatch(request);
        assertThat(response.decision()).isEqualTo("APPROVE: looks good");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsMapResult() {
        var agent = AgentRef.external("judge", ctx ->
                CompletableFuture.completedFuture(
                        new AgentResult(null, Map.of("decision", "approve", "confidence", 0.9),
                                Duration.ZERO, AgentResult.AgentResultStatus.SUCCESS)));
        var caller = CallerRef.agent("judge-1", agent);
        var dispatcher = new DirectJudgmentDispatcher();
        var response = dispatcher.dispatch(new JudgmentDispatchRequest(ctx(), caller, null));
        assertThat(response.decision()).isEqualTo("approve");
    }

    @Test
    void mapsJudgmentResponseDirectly() {
        var expected = new JudgmentResponse("direct", List.of(),
                io.casehub.api.spi.judgment.CallerIdentity.of("test", "agent"));
        var agent = AgentRef.external("judge", ctx ->
                CompletableFuture.completedFuture(
                        new AgentResult(null, expected, Duration.ZERO,
                                AgentResult.AgentResultStatus.SUCCESS)));
        var caller = CallerRef.agent("judge-1", agent);
        var dispatcher = new DirectJudgmentDispatcher();
        var response = dispatcher.dispatch(new JudgmentDispatchRequest(ctx(), caller, null));
        assertThat(response).isSameAs(expected);
    }

    @Test
    void nullAgentRef_throwsIllegalArgument() {
        var caller = CallerRef.agent("no-ref", null);
        var dispatcher = new DirectJudgmentDispatcher();
        assertThatThrownBy(() -> dispatcher.dispatch(new JudgmentDispatchRequest(ctx(), caller, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentRef");
    }
}
