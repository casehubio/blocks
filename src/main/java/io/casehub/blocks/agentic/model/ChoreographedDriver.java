package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Event-driven reactive driver that composes the five agentic SPIs into an
 * event-reactive execution cycle: route -> activate -> dispatch -> aggregate -> terminate.
 *
 * <p>Structurally similar to {@link OrchestratedDriver}, but operates in a reactive mode
 * where agents are activated based on events rather than imperative loops. For the initial
 * implementation, the "event" is the completion of the previous cycle — full event-bus
 * integration (Vert.x EventBus, qhorus channel observation) is deferred to engine integration.
 *
 * <p>Key behavioral differences from OrchestratedDriver:
 * <ul>
 *   <li>Starts in {@link ExecutionState.WaitingForEvent} rather than {@link ExecutionState.Running}</li>
 *   <li>Transitions back to {@link ExecutionState.WaitingForEvent} between cycles</li>
 *   <li>Conceptually event-reactive: agents fire when their activation conditions are met by external events</li>
 * </ul>
 *
 * <p>Agent invocation currently supports {@link AgentRef.ExternalAgent} via its
 * completion-stage function. Other AgentRef variants return a failure result —
 * runtime dispatch integration is deferred to a later task.
 */
public class ChoreographedDriver<T> extends AbstractExecutionDriver<T> {

    public ChoreographedDriver() {
        super();
    }

    public ChoreographedDriver(AgentInvoker<T> invoker) {
        super(invoker);
    }

    @Override
    protected Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context) {
        return Uni.createFrom().item(() -> {
            var start = Instant.now();
            var allResults = new ArrayList<AgentResult>();
            int iteration = 0;

            transition(model, new ExecutionState.WaitingForEvent());

            while (!isCancelled()) {
                var result = executeIteration(model, context, iteration, start, allResults);
                if (result != null) return result;

                iteration++;
                transition(model, new ExecutionState.WaitingForEvent());
            }

            transition(model, new ExecutionState.Cancelled());
            return new ExecutionResult.Cancelled();
        });
    }
}
