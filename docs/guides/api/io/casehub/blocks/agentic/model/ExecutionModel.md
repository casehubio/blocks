# io.casehub.blocks.agentic.model.ExecutionModel

**Package:** `io.casehub.blocks.agentic.model`

**Kind:** `record`

## Fields

### `activation` (`io.casehub.blocks.agentic.activation.ActivationRule<T>`)

### `aggregation` (`io.casehub.blocks.agentic.aggregation.AggregationStrategy<T>`)

### `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>>`)

### `decomposition` (`DecompositionStrategy<T>`)

### `failurePolicy` (`io.casehub.blocks.agentic.FailurePolicy`)

### `listeners` (`java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener>`)

### `patternType` (`io.casehub.blocks.agentic.model.PatternType`)

### `routing` (`io.casehub.blocks.agentic.routing.RoutingStrategy<T>`)

### `task` (`java.lang.String`)

### `termination` (`io.casehub.blocks.agentic.termination.TerminationCondition<T>`)

## Record Components

### `activation` (`io.casehub.blocks.agentic.activation.ActivationRule<T>`)

### `aggregation` (`io.casehub.blocks.agentic.aggregation.AggregationStrategy<T>`)

### `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>>`)

### `decomposition` (`DecompositionStrategy<T>`)

### `failurePolicy` (`io.casehub.blocks.agentic.FailurePolicy`)

### `listeners` (`java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener>`)

### `patternType` (`io.casehub.blocks.agentic.model.PatternType`)

### `routing` (`io.casehub.blocks.agentic.routing.RoutingStrategy<T>`)

### `task` (`java.lang.String`)

### `termination` (`io.casehub.blocks.agentic.termination.TerminationCondition<T>`)

## Constructors

### `public ExecutionModel(io.casehub.blocks.agentic.routing.RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition, io.casehub.blocks.agentic.activation.ActivationRule<T> activation, io.casehub.blocks.agentic.aggregation.AggregationStrategy<T> aggregation, io.casehub.blocks.agentic.termination.TerminationCondition<T> termination, java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>> candidateSupplier, io.casehub.blocks.agentic.FailurePolicy failurePolicy, java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener> listeners, java.lang.String task)`

#### Parameters

- `routing` (`io.casehub.blocks.agentic.routing.RoutingStrategy<T>`)
- `decomposition` (`DecompositionStrategy<T>`)
- `activation` (`io.casehub.blocks.agentic.activation.ActivationRule<T>`)
- `aggregation` (`io.casehub.blocks.agentic.aggregation.AggregationStrategy<T>`)
- `termination` (`io.casehub.blocks.agentic.termination.TerminationCondition<T>`)
- `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>>`)
- `failurePolicy` (`io.casehub.blocks.agentic.FailurePolicy`)
- `listeners` (`java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener>`)
- `task` (`java.lang.String`)

### `public ExecutionModel(io.casehub.blocks.agentic.routing.RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition, io.casehub.blocks.agentic.activation.ActivationRule<T> activation, io.casehub.blocks.agentic.aggregation.AggregationStrategy<T> aggregation, io.casehub.blocks.agentic.termination.TerminationCondition<T> termination, java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>> candidateSupplier, io.casehub.blocks.agentic.FailurePolicy failurePolicy, java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener> listeners, java.lang.String task, io.casehub.blocks.agentic.model.PatternType patternType)`

#### Parameters

- `routing` (`io.casehub.blocks.agentic.routing.RoutingStrategy<T>`)
- `decomposition` (`DecompositionStrategy<T>`)
- `activation` (`io.casehub.blocks.agentic.activation.ActivationRule<T>`)
- `aggregation` (`io.casehub.blocks.agentic.aggregation.AggregationStrategy<T>`)
- `termination` (`io.casehub.blocks.agentic.termination.TerminationCondition<T>`)
- `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>>`)
- `failurePolicy` (`io.casehub.blocks.agentic.FailurePolicy`)
- `listeners` (`java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener>`)
- `task` (`java.lang.String`)
- `patternType` (`io.casehub.blocks.agentic.model.PatternType`)

## Methods

### `public io.casehub.blocks.agentic.activation.ActivationRule<T> activation()`

### `public io.casehub.blocks.agentic.aggregation.AggregationStrategy<T> aggregation()`

### `public java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>> candidateSupplier()`

### `public DecompositionStrategy<T> decomposition()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.blocks.agentic.FailurePolicy failurePolicy()`

### `public final int hashCode()`

### `public java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener> listeners()`

### `public io.casehub.blocks.agentic.model.PatternType patternType()`

### `public io.casehub.blocks.agentic.routing.RoutingStrategy<T> routing()`

### `public java.lang.String task()`

### `public io.casehub.blocks.agentic.termination.TerminationCondition<T> termination()`

### `public final java.lang.String toString()`
