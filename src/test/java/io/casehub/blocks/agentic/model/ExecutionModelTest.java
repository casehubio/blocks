package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionModelTest {

    private final Supplier<List<RoutingCandidate>> candidates = List::of;

    @Test
    void rejectsNullRouting() {
        assertThatThrownBy(() -> new ExecutionModel<>(
                null, new IdentityDecomposition<>(), new OnExplicitDispatch<>(),
                new PassThrough<>(), new MaxIterationsTermination<>(1),
                candidates, FailurePolicy.defaults(), List.of(), "test"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("routing");
    }

    @Test
    void rejectsNullTask() {
        assertThatThrownBy(() -> new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("task");
    }

    @Test
    void acceptsValidConstruction() {
        var model = new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), "my task");
        assertThat(model.task()).isEqualTo("my task");
    }
}
