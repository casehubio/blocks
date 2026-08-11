package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class FirstMatchRoutingTest {

    @Test
    void selectsFirstMatchingCandidate() {
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

        Predicate<RoutingCandidate> matchSecond = c -> c.ref() == agent2;
        var routing = new FirstMatchRouting<Object>(matchSecond);

        var decision = routing.route(new RoutingContext<>("task", candidates, null))
                .await().indefinitely();

        assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
        assertThat(((RoutingDecision.Selected) decision).agents()).containsExactly(agent2);
    }

    @Test
    void returnsUnresolvableWhenNoMatch() {
        var worker = Worker.builder()
                .name("w")
                .capabilityNames("test")
                .function(x -> WorkerResult.of(Map.of()))
                .build();
        var candidates = List.of(new RoutingCandidate(new AgentRef.WorkerAgent(worker), null));
        var routing = new FirstMatchRouting<Object>(c -> false);

        var decision = routing.route(new RoutingContext<>("task", candidates, null))
                .await().indefinitely();

        assertThat(decision).isInstanceOf(RoutingDecision.Unresolvable.class);
    }
}
