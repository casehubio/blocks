# io.casehub.blocks.agentic.decomposition.GoapDecompositionContext

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `record`

## Fields

### `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `availableTypes` (`java.util.Set<java.lang.String>`)

### `depth` (`int`)

### `goalTypes` (`java.util.Set<java.lang.String>`)

### `state` (`T`)

## Record Components

### `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `availableTypes` (`java.util.Set<java.lang.String>`)

### `depth` (`int`)

### `goalTypes` (`java.util.Set<java.lang.String>`)

### `state` (`T`)

## Constructors

### `public GoapDecompositionContext(T state, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents, int depth, java.util.Set<java.lang.String> goalTypes, java.util.Set<java.lang.String> availableTypes)`

#### Parameters

- `state` (`T`)
- `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)
- `depth` (`int`)
- `goalTypes` (`java.util.Set<java.lang.String>`)
- `availableTypes` (`java.util.Set<java.lang.String>`)

## Methods

### `public java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents()`

### `public java.util.Set<java.lang.String> availableTypes()`

### `public int depth()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Set<java.lang.String> goalTypes()`

### `public final int hashCode()`

### `public T state()`

### `public final java.lang.String toString()`
