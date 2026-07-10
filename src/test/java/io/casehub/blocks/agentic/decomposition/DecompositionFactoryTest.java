package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class DecompositionFactoryTest {

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    @Test
    void primitiveFactoryBackwardCompatible() {
        var agent = dummyAgent();
        var task = Decomposition.primitive(agent);
        assertThat(task.agent()).isSameAs(agent);
        assertThat(task.description()).isNull();
        assertThat(task.precondition()).isNull();
        assertThat(task.effect()).isNull();
    }

    @Test
    void primitiveFactoryWithDescription() {
        var agent = dummyAgent();
        var task = Decomposition.primitive("build artifact", agent);
        assertThat(task.description()).isEqualTo("build artifact");
        assertThat(task.agent()).isSameAs(agent);
    }

    @Test
    void plannedFactoryCreatesPlannedTask() {
        var agent = dummyAgent();
        var task = Decomposition.planned("analyse data", agent);
        assertThat(task.description()).isEqualTo("analyse data");
        assertThat(task.agent()).isSameAs(agent);
        assertThat(task.rationale()).isNull();
    }

    @Test
    void plannedFactoryWithRationale() {
        var agent = dummyAgent();
        var task = Decomposition.planned("analyse data", agent, "domain expert");
        assertThat(task.description()).isEqualTo("analyse data");
        assertThat(task.agent()).isSameAs(agent);
        assertThat(task.rationale()).isEqualTo("domain expert");
    }
}
