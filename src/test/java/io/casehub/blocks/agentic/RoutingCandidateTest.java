package io.casehub.blocks.agentic;

import io.casehub.worker.api.WorkerResult;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RoutingCandidateTest {

    private static AgentRef createTestAgent() {
        var worker = Worker.builder()
                .name("test")
                .capabilityNames("test")
                .function(x -> WorkerResult.of(Map.of()))
                .build();
        return new AgentRef.WorkerAgent(worker);
    }

    @Test
    void carriesRefAndDescriptor() {
        var ref = createTestAgent();
        var descriptor = mock(AgentDescriptor.class);
        var candidate = new RoutingCandidate(ref, descriptor);
        assertThat(candidate.ref()).isEqualTo(ref);
        assertThat(candidate.descriptor()).isEqualTo(descriptor);
    }

    @Test
    void descriptorIsNullable() {
        var ref = createTestAgent();
        var candidate = new RoutingCandidate(ref, null);
        assertThat(candidate.descriptor()).isNull();
    }

    @Test
    void rejectsNullRef() {
        assertThatThrownBy(() -> new RoutingCandidate(null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ref");
    }

    @Test
    void acceptsNullDescriptor() {
        var candidate = new RoutingCandidate(
                AgentRef.external(x -> java.util.concurrent.CompletableFuture.completedFuture(AgentResult.success(null, "ok"))),
                null);
        assertThat(candidate.descriptor()).isNull();
    }
}
