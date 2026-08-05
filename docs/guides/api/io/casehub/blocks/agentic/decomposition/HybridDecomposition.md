# io.casehub.blocks.agentic.decomposition.HybridDecomposition

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `fallbackStrategy` (`DecompositionStrategy<T>`)

### `primaryStrategy` (`DecompositionStrategy<T>`)

## Constructors

### `public HybridDecomposition(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public HybridDecomposition(AgentProvider agentProvider, int maxDepth)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `maxDepth` (`int`)

### `public HybridDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

### `public HybridDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer, int maxDepth)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)
- `maxDepth` (`int`)

### `public HybridDecomposition(DecompositionStrategy<T> primaryStrategy, DecompositionStrategy<T> fallbackStrategy)`

#### Parameters

- `primaryStrategy` (`DecompositionStrategy<T>`)
- `fallbackStrategy` (`DecompositionStrategy<T>`)

## Methods

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> compound, DecompositionContext<T> context)`

#### Parameters

- `compound` (`TaskNode<T>`)
- `context` (`DecompositionContext<T>`)

### `private DecompositionContext<T> enrichContext(DecompositionContext<T> context, io.casehub.blocks.agentic.decomposition.NoMethodMatchedException failure)`

#### Parameters

- `context` (`DecompositionContext<T>`)
- `failure` (`io.casehub.blocks.agentic.decomposition.NoMethodMatchedException`)

### `private static java.lang.String taskName(TaskNode<T> node)`

#### Parameters

- `node` (`TaskNode<T>`)
