package io.casehub.blocks.agentic.decomposition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoMethodMatchedExceptionTest {

    @Test
    void carriesTaskName() {
        var ex = new NoMethodMatchedException("deploy-app");
        assertThat(ex.taskName()).isEqualTo("deploy-app");
    }

    @Test
    void extendsIllegalStateException() {
        var ex = new NoMethodMatchedException("task");
        assertThat(ex).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void messageIncludesTaskName() {
        var ex = new NoMethodMatchedException("my-task");
        assertThat(ex.getMessage()).contains("my-task");
    }
}
