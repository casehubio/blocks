package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentResult;

import java.time.Instant;
import java.util.ArrayList;

public class OrchestratedDriver<T> extends AbstractExecutionDriver<T> {

    public OrchestratedDriver() {
        super();
    }

    public OrchestratedDriver(AgentInvoker<T> invoker) {
        super(invoker);
    }

    @Override
    protected ExecutionResult runLoop(ExecutionModel<T> model, T context) {
        var start      = Instant.now();
        var allResults = new ArrayList<AgentResult>();
        int iteration  = 0;

        while (!isCancelled()) {
            transition(model, new ExecutionState.Running(iteration));

            var result = executeIteration(model, context, iteration, start, allResults);
            if (result != null) {return result;}

            iteration++;
        }

        transition(model, new ExecutionState.Cancelled());
        return new ExecutionResult.Cancelled();
    }
}
