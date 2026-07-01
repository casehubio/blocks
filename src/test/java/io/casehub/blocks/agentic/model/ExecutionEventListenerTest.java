package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEventListenerTest {

    @Test
    void agentNameReturnsWorkerName() {
        var worker = Worker.builder()
                .name("clinical-reviewer")
                .capabilityName("review")
                .function(WorkerFunction.NONE)
                .build();
        assertThat(ExecutionEventListener.agentName(AgentRef.worker(worker)))
                .isEqualTo("clinical-reviewer");
    }

    @Test
    void agentNameReturnsChannelId() {
        var id = UUID.randomUUID();
        var agent = AgentRef.channel(id, null);
        assertThat(ExecutionEventListener.agentName(agent))
                .isEqualTo("channel:" + id);
    }

    @Test
    void agentNameDistinguishesExternalAgents() {
        var a = AgentRef.external(x -> CompletableFuture.completedFuture(AgentResult.success(null, "a")));
        var b = AgentRef.external(x -> CompletableFuture.completedFuture(AgentResult.success(null, "b")));
        var nameA = ExecutionEventListener.agentName(a);
        var nameB = ExecutionEventListener.agentName(b);
        assertThat(nameA).startsWith("external:");
        assertThat(nameB).startsWith("external:");
        assertThat(nameA).isNotEqualTo(nameB);
    }

    @Test
    void agentNameReturnsHuman() {
        assertThat(ExecutionEventListener.agentName(AgentRef.human(null)))
                .isEqualTo("human");
    }
}
