# io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `record`

## Fields

### `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `decomposer` (`DecompositionStrategy<T>`)

### `depth` (`int`)

### `parentGoal` (`java.lang.String`)

### `siblingNames` (`java.util.List<java.lang.String>`)

### `state` (`T`)

### `staticFailureHint` (`java.lang.String`)

### `subtaskDescription` (`java.lang.String`)

## Record Components

### `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `decomposer` (`DecompositionStrategy<T>`)

### `depth` (`int`)

### `parentGoal` (`java.lang.String`)

### `siblingNames` (`java.util.List<java.lang.String>`)

### `state` (`T`)

### `staticFailureHint` (`java.lang.String`)

### `subtaskDescription` (`java.lang.String`)

## Constructors

### `public AgenticDecompositionContext(T state, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents, int depth)`

#### Parameters

- `state` (`T`)
- `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)
- `depth` (`int`)

### `public AgenticDecompositionContext(T state, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents, int depth, java.lang.String staticFailureHint)`

#### Parameters

- `state` (`T`)
- `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)
- `depth` (`int`)
- `staticFailureHint` (`java.lang.String`)

### `public AgenticDecompositionContext(T state, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents, int depth, java.lang.String staticFailureHint, java.lang.String subtaskDescription, java.lang.String parentGoal, java.util.List<java.lang.String> siblingNames, DecompositionStrategy<T> decomposer)`

#### Parameters

- `state` (`T`)
- `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)
- `depth` (`int`)
- `staticFailureHint` (`java.lang.String`)
- `subtaskDescription` (`java.lang.String`)
- `parentGoal` (`java.lang.String`)
- `siblingNames` (`java.util.List<java.lang.String>`)
- `decomposer` (`DecompositionStrategy<T>`)

## Methods

### `public java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents()`

### `public DecompositionStrategy<T> decomposer()`

### `public int depth()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String parentGoal()`

### `public java.util.List<java.lang.String> siblingNames()`

### `public T state()`

### `public java.lang.String staticFailureHint()`

### `public java.lang.String subtaskDescription()`

### `public final java.lang.String toString()`
