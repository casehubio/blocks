# io.casehub.blocks.agentic.decomposition.Decomposition

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Constructors

### `private Decomposition()`

## Methods

### `public static TaskNode.CompoundTask<T> compound(java.lang.String name, java.util.List<DecompositionMethod<T>> methods)`

#### Parameters

- `name` (`java.lang.String`)
- `methods` (`java.util.List<DecompositionMethod<T>>`)

### `public static io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition<T> goap()`

### `public static io.casehub.blocks.agentic.decomposition.HeuristicDecomposition<T> heuristic(io.casehub.blocks.agentic.decomposition.DecompositionHeuristic<T> heuristic)`

#### Parameters

- `heuristic` (`io.casehub.blocks.agentic.decomposition.DecompositionHeuristic<T>`)

### `public static io.casehub.blocks.agentic.decomposition.HybridDecomposition<T> hybrid(io.casehub.platform.agent.AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`io.casehub.platform.agent.AgentProvider`)

### `public static io.casehub.blocks.agentic.decomposition.HybridDecomposition<T> hybrid(io.casehub.platform.agent.AgentProvider agentProvider, int maxDepth)`

#### Parameters

- `agentProvider` (`io.casehub.platform.agent.AgentProvider`)
- `maxDepth` (`int`)

### `public static io.casehub.blocks.agentic.decomposition.HybridDecomposition<T> hybrid(io.casehub.platform.agent.AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer)`

#### Parameters

- `agentProvider` (`io.casehub.platform.agent.AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

### `public static io.casehub.blocks.agentic.decomposition.HybridDecomposition<T> hybrid(io.casehub.platform.agent.AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer, int maxDepth)`

#### Parameters

- `agentProvider` (`io.casehub.platform.agent.AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)
- `maxDepth` (`int`)

### `public static DecompositionMethod<T> method(java.util.function.Predicate<T> guard, DecompositionStrategy<T> strategy)`

#### Parameters

- `guard` (`java.util.function.Predicate<T>`)
- `strategy` (`DecompositionStrategy<T>`)

### `public static io.casehub.blocks.agentic.decomposition.IdentityDecomposition<T> none()`

### `public static io.casehub.blocks.agentic.decomposition.PlannedTask<T> planned(java.lang.String description, io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `description` (`java.lang.String`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `public static io.casehub.blocks.agentic.decomposition.PlannedTask<T> planned(java.lang.String description, io.casehub.blocks.agentic.AgentRef agent, java.lang.String rationale)`

#### Parameters

- `description` (`java.lang.String`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `rationale` (`java.lang.String`)

### `public static io.casehub.blocks.agentic.decomposition.PrimitiveTask<T> primitive(io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `public static io.casehub.blocks.agentic.decomposition.PrimitiveTask<T> primitive(java.lang.String description, io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `description` (`java.lang.String`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `public static DecompositionStrategy<T> sequence(TaskNode<T>[] tasks)`

#### Parameters

- `tasks` (`TaskNode<T>[]`)

### `public static io.casehub.blocks.agentic.decomposition.StaticDecomposition<T> staticTree()`
