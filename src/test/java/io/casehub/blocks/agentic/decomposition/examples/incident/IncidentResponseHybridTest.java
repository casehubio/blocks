package io.casehub.blocks.agentic.decomposition.examples.incident;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext;
import io.casehub.blocks.agentic.decomposition.HybridDecomposition;
import io.casehub.blocks.agentic.decomposition.PlannedTask;
import io.casehub.blocks.agentic.decomposition.PrimitiveTask;
import io.casehub.blocks.agentic.decomposition.StaticDecomposition;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.casehub.engine.plan.DagPlan;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentResponseHybridTest {

    record IncidentState(String type, String severity, String description) {}

    private static AgentRef namedAgent(String name) {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, name + "-done")));
    }

    private static RoutingCandidate candidate(String name, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(name).name(name).slot("default").tenancyId("test")
                .briefing(briefing)
                .capabilities(List.of(AgentCapability.builder().name("incident-response").build()))
                .build();
        return new RoutingCandidate(namedAgent(name), descriptor);
    }

    private final AgentRef failoverAgent = namedAgent("failover-db");
    private final AgentRef verifyAgent = namedAgent("verify-connectivity");
    private final AgentRef notifyAgent = namedAgent("notify-stakeholders");
    private final AgentRef isolateAgent = namedAgent("isolate-systems");
    private final AgentRef forensicsAgent = namedAgent("forensic-analysis");
    private final AgentRef diagnoseAgent = namedAgent("diagnose-network");
    private final AgentRef rerouteAgent = namedAgent("reroute-traffic");

    private TaskNode.CompoundTask<IncidentState> incidentTree() {
        return new TaskNode.CompoundTask<>("respond-to-incident", List.of(
                new DecompositionMethod<>(
                        state -> "DATABASE_OUTAGE".equals(state.type()),
                        (compound, ctx) -> Uni.createFrom().item(DagPlan.sequence(List.of(
                                new PrimitiveTask<>("t1", Instant.now(), "failover-db", failoverAgent, null, null),
                                new PrimitiveTask<>("t2", Instant.now(), "verify-connectivity", verifyAgent, null, null),
                                new PrimitiveTask<>("t3", Instant.now(), "notify-stakeholders", notifyAgent, null, null))))),
                new DecompositionMethod<>(
                        state -> "SECURITY_BREACH".equals(state.type()),
                        (compound, ctx) -> Uni.createFrom().item(DagPlan.sequence(List.of(
                                new PrimitiveTask<>("t4", Instant.now(), "isolate-systems", isolateAgent, null, null),
                                new PrimitiveTask<>("t5", Instant.now(), "forensic-analysis", forensicsAgent, null, null),
                                new PrimitiveTask<>("t6", Instant.now(), "notify-stakeholders", notifyAgent, null, null))))),
                new DecompositionMethod<>(
                        state -> "NETWORK_FAILURE".equals(state.type()),
                        (compound, ctx) -> Uni.createFrom().item(DagPlan.sequence(List.of(
                                new PrimitiveTask<>("t7", Instant.now(), "diagnose-network", diagnoseAgent, null, null),
                                new PrimitiveTask<>("t8", Instant.now(), "reroute-traffic", rerouteAgent, null, null),
                                new PrimitiveTask<>("t9", Instant.now(), "notify-stakeholders", notifyAgent, null, null)))))
        ));
    }

    @Test
    void databaseOutage_staticPlaybook() {
        var hybrid = new HybridDecomposition<>(new StaticDecomposition<IncidentState>(),
                (compound, ctx) -> Uni.createFrom().failure(new AssertionError("LLM should not be called")));
        var state = new IncidentState("DATABASE_OUTAGE", "HIGH", "DB cluster down");
        var ctx = new AgenticDecompositionContext<>(state, List.of(), 0);

        var plan = hybrid.decompose(incidentTree(), ctx).await().indefinitely();

        assertThat(plan.nodes()).hasSize(3);
        var tasks = plan.topologicalSort();
        assertThat(tasks.get(0).task().description()).isEqualTo("failover-db");
        assertThat(tasks.get(1).task().description()).isEqualTo("verify-connectivity");
        assertThat(tasks.get(2).task().description()).isEqualTo("notify-stakeholders");
    }

    @Test
    void securityBreach_staticPlaybook() {
        var hybrid = new HybridDecomposition<>(new StaticDecomposition<IncidentState>(),
                (compound, ctx) -> Uni.createFrom().failure(new AssertionError("LLM should not be called")));
        var state = new IncidentState("SECURITY_BREACH", "CRITICAL", "Unauthorised access detected");
        var ctx = new AgenticDecompositionContext<>(state, List.of(), 0);

        var plan = hybrid.decompose(incidentTree(), ctx).await().indefinitely();

        assertThat(plan.nodes()).hasSize(3);
        var tasks = plan.topologicalSort();
        assertThat(tasks.get(0).task().description()).isEqualTo("isolate-systems");
        assertThat(tasks.get(1).task().description()).isEqualTo("forensic-analysis");
        assertThat(tasks.get(2).task().description()).isEqualTo("notify-stakeholders");
    }

    @Test
    void novelIncident_llmFallback() {
        var llmJson = """
                [{"agent": "diagnose-network", "task": "check latency and throughput metrics", "rationale": "performance issues often start at network layer"},
                 {"agent": "notify-stakeholders", "task": "alert ops team", "rationale": "escalation needed"}]
                """;
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(llmJson)));

        var agents = List.of(
                candidate("diagnose-network", "network diagnostics"),
                candidate("notify-stakeholders", "stakeholder notification"),
                candidate("failover-db", "database failover"),
                candidate("verify-connectivity", "connectivity verification"));

        var hybrid = new HybridDecomposition<IncidentState>(provider, state -> state.description());
        var state = new IncidentState("PERFORMANCE_DEGRADATION", "MEDIUM", "API response times elevated");
        var ctx = new AgenticDecompositionContext<>(state, agents, 0);

        var plan = hybrid.decompose(incidentTree(), ctx).await().indefinitely();

        assertThat(plan.nodes()).hasSize(2);
        var tasks = plan.topologicalSort();
        assertThat(tasks.get(0).task()).isInstanceOf(PlannedTask.class);
        assertThat(tasks.get(0).task().description()).isEqualTo("check latency and throughput metrics");
        assertThat(tasks.get(1).task().description()).isEqualTo("alert ops team");
    }
}
