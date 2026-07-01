package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.*;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChoreographedDriverTest {

    @Test
    void executesReactivelyOnEvents() {
        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object input) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "reactive"));
        });
        var candidate = new RoutingCandidate(agent, null);

        var model = new ExecutionModel<String>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                new MaxIterationsTermination<>(3),
                () -> List.of(candidate),
                FailurePolicy.defaults(),
                List.of(), "test");

        var driver = new ChoreographedDriver<String>();
        var result = driver.execute(model, "state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(callCount.get()).isEqualTo(3);
    }
}
