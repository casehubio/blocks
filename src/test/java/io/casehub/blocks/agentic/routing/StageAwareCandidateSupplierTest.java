package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class StageAwareCandidateSupplierTest {

    private static RoutingCandidate candidateFor(String name) {
        var worker = Worker.builder().name(name).capabilityName("default").noFunction().build();
        return new RoutingCandidate(AgentRef.worker(worker), null);
    }

    @Test
    void freeFloatingCandidatesAlwaysPass() {
        var c1 = candidateFor("free-agent");
        Supplier<List<RoutingCandidate>> delegate = () -> List.of(c1);
        var gate = new StageGate() {
            public Set<String> allStagedBindings() { return Set.of("staged-only"); }
            public Set<String> activeStagedBindings() { return Set.of(); }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).containsExactly(c1);
    }

    @Test
    void stagedCandidateFilteredWhenStageNotActive() {
        var c1 = candidateFor("gated-worker");
        Supplier<List<RoutingCandidate>> delegate = () -> List.of(c1);
        var gate = new StageGate() {
            public Set<String> allStagedBindings() { return Set.of("gated-worker"); }
            public Set<String> activeStagedBindings() { return Set.of(); }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).isEmpty();
    }

    @Test
    void stagedCandidatePassesWhenStageActive() {
        var c1 = candidateFor("gated-worker");
        Supplier<List<RoutingCandidate>> delegate = () -> List.of(c1);
        var gate = new StageGate() {
            public Set<String> allStagedBindings() { return Set.of("gated-worker"); }
            public Set<String> activeStagedBindings() { return Set.of("gated-worker"); }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).containsExactly(c1);
    }

    @Test
    void mixedFreeAndStagedFilteredCorrectly() {
        var free = candidateFor("free-agent");
        var gatedActive = candidateFor("active-gated");
        var gatedInactive = candidateFor("inactive-gated");
        Supplier<List<RoutingCandidate>> delegate = () -> List.of(free, gatedActive, gatedInactive);

        var gate = new StageGate() {
            public Set<String> allStagedBindings() {
                return Set.of("active-gated", "inactive-gated");
            }
            public Set<String> activeStagedBindings() {
                return Set.of("active-gated");
            }
        };

        var supplier = new StageAwareCandidateSupplier(
                delegate, gate, ref -> ((AgentRef.WorkerAgent) ref).worker().name());

        assertThat(supplier.get()).containsExactly(free, gatedActive);
    }
}
