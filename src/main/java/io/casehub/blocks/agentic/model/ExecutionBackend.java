package io.casehub.blocks.agentic.model;

import io.smallrye.mutiny.Uni;

@FunctionalInterface
public interface ExecutionBackend<T> {

    Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext);

    static <T> ExecutionBackend<T> reactive() {
        return (model, ctx) -> new OrchestratedDriver<T>().execute(model, ctx);
    }

    static <T> ExecutionBackend<T> reactive(AgentInvoker<T> invoker) {
        return (model, ctx) -> new OrchestratedDriver<>(invoker).execute(model, ctx);
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
