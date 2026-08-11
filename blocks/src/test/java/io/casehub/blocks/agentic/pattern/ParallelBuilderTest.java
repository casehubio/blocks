package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static io.casehub.blocks.agentic.pattern.Patterns.parallel;
import static org.assertj.core.api.Assertions.assertThat;

class ParallelBuilderTest {

    @Test
    void dispatchesAllAgents() {
        var count = new AtomicInteger(0);
        var a1 = AgentRef.external((Object i) -> {
            count.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "a"));
        });
        var a2 = AgentRef.external((Object i) -> {
            count.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "b"));
        });

        var result = parallel()
                .agents(a1, a2)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(count.get()).isEqualTo(2);
    }
}
