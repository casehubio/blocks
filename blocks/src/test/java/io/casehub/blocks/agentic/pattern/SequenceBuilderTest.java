package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static io.casehub.blocks.agentic.pattern.Patterns.sequence;
import static org.assertj.core.api.Assertions.assertThat;

class SequenceBuilderTest {

    @Test
    void executesAgentsInOrder() {
        var order = new ArrayList<String>();
        var a1 = AgentRef.external((Object i) -> {
            order.add("first");
            return CompletableFuture.completedFuture(AgentResult.success(null, "1"));
        });
        var a2 = AgentRef.external((Object i) -> {
            order.add("second");
            return CompletableFuture.completedFuture(AgentResult.success(null, "2"));
        });

        var result = sequence()
                .agents(a1, a2)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(order).containsExactly("first", "second");
    }

    @Test
    void multipleBuildCallsCreateIndependentModels() {
        var order1 = new ArrayList<String>();
        var order2 = new ArrayList<String>();

        var a1 = AgentRef.external((Object i) -> {
            order1.add("a1");
            return CompletableFuture.completedFuture(AgentResult.success(null, "1"));
        });
        var a2 = AgentRef.external((Object i) -> {
            order1.add("a2");
            return CompletableFuture.completedFuture(AgentResult.success(null, "2"));
        });

        var b1 = AgentRef.external((Object i) -> {
            order2.add("b1");
            return CompletableFuture.completedFuture(AgentResult.success(null, "3"));
        });
        var b2 = AgentRef.external((Object i) -> {
            order2.add("b2");
            return CompletableFuture.completedFuture(AgentResult.success(null, "4"));
        });

        var builder = sequence();
        var model1 = builder.agents(a1, a2).build();
        var model2 = builder.agents(b1, b2).build();

        // Execute both models using the driver
        var driver = new OrchestratedDriver<>();
        var result1 = driver.execute(model1, "state1").await().indefinitely();
        var result2 = driver.execute(model2, "state2").await().indefinitely();

        // Verify both completed successfully and maintained independent state
        assertThat(result1).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(result2).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(order1).containsExactly("a1", "a2");
        assertThat(order2).containsExactly("b1", "b2");
    }
}
