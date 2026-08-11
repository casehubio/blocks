package io.casehub.blocks.agentic.model;

import io.smallrye.mutiny.Uni;

@FunctionalInterface
public interface ExecutionBackend<T> {

    Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext);

    default void cancel() {}

    static <T> ExecutionBackend<T> reactive() {
        return new CancellableBackend<>(new OrchestratedDriver<>());
    }

    static <T> ExecutionBackend<T> reactive(AgentInvoker<T> invoker) {
        return new CancellableBackend<>(new OrchestratedDriver<>(invoker));
    }

    @Deprecated
    static <T> ExecutionBackend<T> orchestrated() {
        return reactive();
    }

    @Deprecated
    static <T> ExecutionBackend<T> orchestrated(AgentInvoker<T> invoker) {
        return reactive(invoker);
    }
}
