# io.casehub.blocks.agentic.decomposition.LlmDecompositionHeuristic

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `MAPPER` (`ObjectMapper`)

### `SYSTEM_PROMPT` (`java.lang.String`)

### `agentProvider` (`AgentProvider`)

### `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

## Constructors

### `public LlmDecompositionHeuristic(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public LlmDecompositionHeuristic(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

## Methods

### `private java.lang.String buildPrompt(TaskNode.CompoundTask<T> task, java.util.List<DecompositionMethod<T>> methods, DecompositionContext<T> context)`

#### Parameters

- `task` (`TaskNode.CompoundTask<T>`)
- `methods` (`java.util.List<DecompositionMethod<T>>`)
- `context` (`DecompositionContext<T>`)

### `private java.lang.String describeMethod(DecompositionMethod<T> method)`

#### Parameters

- `method` (`DecompositionMethod<T>`)

### `private java.lang.String describeNode(TaskNode<T> node)`

#### Parameters

- `node` (`TaskNode<T>`)

### `public Uni<java.util.List<io.casehub.blocks.agentic.decomposition.ScoredMethod<T>>> evaluate(TaskNode.CompoundTask<T> task, java.util.List<DecompositionMethod<T>> methods, DecompositionContext<T> context)`

#### Parameters

- `task` (`TaskNode.CompoundTask<T>`)
- `methods` (`java.util.List<DecompositionMethod<T>>`)
- `context` (`DecompositionContext<T>`)

### `private java.util.List<io.casehub.blocks.agentic.decomposition.ScoredMethod<T>> parseResponse(java.lang.String text, java.util.List<DecompositionMethod<T>> methods)`

#### Parameters

- `text` (`java.lang.String`)
- `methods` (`java.util.List<DecompositionMethod<T>>`)
