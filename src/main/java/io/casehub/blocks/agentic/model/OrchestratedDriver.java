package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Imperative loop driver that composes the five agentic SPIs into a
 * synchronous execution cycle: route -> activate -> dispatch -> aggregate -> terminate.
 *
 * <p>Each iteration refreshes routing candidates, selects agents, dispatches them,
 * aggregates results, and evaluates termination. The loop continues until a
 * termination condition fires, a routing failure triggers the failure policy,
 * or cancellation is requested.
 *
 * <p>Agent invocation currently supports {@link AgentRef.ExternalAgent} via its
 * completion-stage function. Other AgentRef variants return a failure result —
 * runtime dispatch integration is deferred to a later task.
 */
public class OrchestratedDriver<T> extends AbstractExecutionDriver<T> {

    public OrchestratedDriver() {
        super();
    }

    public OrchestratedDriver(AgentInvoker<T> invoker) {
        super(invoker);
    }

    @Override
    protected Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context) {
        return Uni.createFrom().item(() -> {
            var start = Instant.now();
            var allResults = new ArrayList<AgentResult>();
            int iteration = 0;

            while (!isCancelled()) {
                transition(model, new ExecutionState.Running(iteration));

                var result = executeIteration(model, context, iteration, start, allResults);
                if (result != null) return result;

                iteration++;
            }

            transition(model, new ExecutionState.Cancelled());
            return new ExecutionResult.Cancelled();
        });
    }
}
