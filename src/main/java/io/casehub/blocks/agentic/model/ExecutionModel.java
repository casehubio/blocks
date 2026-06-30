package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.decomposition.DecompositionStrategy;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.agentic.termination.TerminationCondition;

import java.util.List;
import java.util.function.Supplier;

public record ExecutionModel<T>(
        RoutingStrategy<T> routing,
        DecompositionStrategy<T> decomposition,
        ActivationRule<T> activation,
        AggregationStrategy<T> aggregation,
        TerminationCondition<T> termination,
        Supplier<List<RoutingCandidate>> candidateSupplier,
        FailurePolicy failurePolicy,
        List<ExecutionEventListener> listeners
) {
    public ExecutionModel {
        listeners = List.copyOf(listeners);
    }
}
