package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ExecutionEventListenerContractTest {

    @Test
    void anonymousImplementationCompiles() {
        ExecutionEventListener listener = new ExecutionEventListener() {};
        assertThatCode(() -> listener.onExecutionStart(null)).doesNotThrowAnyException();
        assertThatCode(() -> listener.onExecutionComplete(null)).doesNotThrowAnyException();
    }
}
