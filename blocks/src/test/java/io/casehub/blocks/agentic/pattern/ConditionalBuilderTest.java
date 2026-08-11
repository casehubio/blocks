package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static io.casehub.blocks.agentic.pattern.Patterns.conditional;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionalBuilderTest {

    @Test
    void routesBasedOnPredicate() {
        var agent = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "routed")));

        var result = conditional()
                .when(c -> true, agent)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }
}
