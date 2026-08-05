# io.casehub.blocks.agentic.pattern.AbstractPatternBuilder

**Package:** `io.casehub.blocks.agentic.pattern`

**Kind:** `class`

## Fields

### `activation` (`io.casehub.blocks.agentic.activation.ActivationRule<T>`)

### `aggregation` (`io.casehub.blocks.agentic.aggregation.AggregationStrategy<T>`)

### `backend` (`io.casehub.blocks.agentic.model.ExecutionBackend<T>`)

### `candidateSupplier` (`java.util.function.Supplier<java.util.List<io.casehub.blocks.agentic.RoutingCandidate>>`)

### `decomposition` (`DecompositionStrategy<T>`)

### `failurePolicy` (`io.casehub.blocks.agentic.FailurePolicy`)

### `listeners` (`java.util.List<io.casehub.blocks.agentic.model.ExecutionEventListener>`)

### `patternType` (`io.casehub.blocks.agentic.model.PatternType`)

### `routing` (`io.casehub.blocks.agentic.routing.RoutingStrategy<T>`)

### `task` (`java.lang.String`)

### `termination` (`io.casehub.blocks.agentic.termination.TerminationCondition<T>`)

## Constructors

### `public AbstractPatternBuilder()`

## Methods

### `public B activate(io.casehub.blocks.agentic.activation.ActivationRule<T> activation)`

#### Parameters

- `activation` (`io.casehub.blocks.agentic.activation.ActivationRule<T>`)

### `protected B agents(io.casehub.blocks.agentic.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.blocks.agentic.AgentRef[]`)

### `protected B agents(io.casehub.blocks.agentic.RoutingCandidate[] candidates)`

#### Parameters

- `candidates` (`io.casehub.blocks.agentic.RoutingCandidate[]`)

### `public B aggregate(io.casehub.blocks.agentic.aggregation.AggregationStrategy<T> aggregation)`

#### Parameters

- `aggregation` (`io.casehub.blocks.agentic.aggregation.AggregationStrategy<T>`)

### `public B backend(io.casehub.blocks.agentic.model.ExecutionBackend<T> backend)`

#### Parameters

- `backend` (`io.casehub.blocks.agentic.model.ExecutionBackend<T>`)

### `public io.casehub.blocks.agentic.model.ExecutionModel<T> build()`

### `public B decompose(DecompositionStrategy<T> decomposition)`

#### Parameters

- `decomposition` (`DecompositionStrategy<T>`)

### `public Uni<io.casehub.blocks.agentic.model.ExecutionResult> execute(T initialContext)`

#### Parameters

- `initialContext` (`T`)

### `public B listener(io.casehub.blocks.agentic.model.ExecutionEventListener listener)`

#### Parameters

- `listener` (`io.casehub.blocks.agentic.model.ExecutionEventListener`)

### `public B maxAgentRetries(int max)`

#### Parameters

- `max` (`int`)

### `public B onDeadlock(io.casehub.blocks.agentic.FailurePolicy.AggregationFailureAction action)`

#### Parameters

- `action` (`io.casehub.blocks.agentic.FailurePolicy.AggregationFailureAction`)

### `public B onRoutingFailure(io.casehub.blocks.agentic.FailurePolicy.RoutingFailureAction action)`

#### Parameters

- `action` (`io.casehub.blocks.agentic.FailurePolicy.RoutingFailureAction`)

### `public B route(io.casehub.blocks.agentic.routing.RoutingStrategy<T> routing)`

#### Parameters

- `routing` (`io.casehub.blocks.agentic.routing.RoutingStrategy<T>`)

### `public B task(java.lang.String task)`

#### Parameters

- `task` (`java.lang.String`)

### `public B terminate(io.casehub.blocks.agentic.termination.TerminationCondition<T> termination)`

#### Parameters

- `termination` (`io.casehub.blocks.agentic.termination.TerminationCondition<T>`)
