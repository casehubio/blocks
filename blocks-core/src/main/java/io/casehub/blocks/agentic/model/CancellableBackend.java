package io.casehub.blocks.agentic.model;

import io.smallrye.mutiny.Uni;

class CancellableBackend<T> implements ExecutionBackend<T> {

    private final AbstractExecutionDriver<T> driver;

    CancellableBackend(AbstractExecutionDriver<T> driver) {
        this.driver = driver;
    }

    @Override
    public Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext) {
        return driver.execute(model, initialContext);
    }

    @Override
    public void cancel() {
        driver.cancel();
    }
}