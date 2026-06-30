package io.casehub.blocks.agentic.model;

import io.smallrye.mutiny.Uni;

public interface ExecutionDriver<T> {
    Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext);
    Uni<Void> cancel();
    ExecutionState state();
}
