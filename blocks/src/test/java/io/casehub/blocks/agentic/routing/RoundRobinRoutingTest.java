package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinRoutingTest {

    @Test
    void cyclesThroughCandidates() {
        var worker1 = Worker.builder()
                .name("w1")
                .capabilityNames("test")
                .function(x -> WorkerResult.of(Map.of()))
                .build();
        var worker2 = Worker.builder()
                .name("w2")
                .capabilityNames("test")
                .function(x -> WorkerResult.of(Map.of()))
                .build();
        var agent1 = new AgentRef.WorkerAgent(worker1);
        var agent2 = new AgentRef.WorkerAgent(worker2);
        var candidates = List.of(
                new RoutingCandidate(agent1, null),
                new RoutingCandidate(agent2, null));

        var routing = new RoundRobinRouting<Object>();
        var ctx = new RoutingContext<>("task", candidates, null);

        var first = (RoutingDecision.Selected) routing.route(ctx).await().indefinitely();
        var second = (RoutingDecision.Selected) routing.route(ctx).await().indefinitely();
        var third = (RoutingDecision.Selected) routing.route(ctx).await().indefinitely();

        assertThat(first.agents()).containsExactly(agent1);
        assertThat(second.agents()).containsExactly(agent2);
        assertThat(third.agents()).containsExactly(agent1);
    }

    @Test
    void returnsUnresolvableForEmptyCandidates() {
        var routing = new RoundRobinRouting<Object>();
        var ctx = new RoutingContext<>("task", List.of(), null);

        var decision = routing.route(ctx).await().indefinitely();
        assertThat(decision).isInstanceOf(RoutingDecision.Unresolvable.class);
    }
}
