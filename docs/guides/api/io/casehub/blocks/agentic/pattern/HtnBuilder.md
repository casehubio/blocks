# io.casehub.blocks.agentic.pattern.HtnBuilder

**Package:** `io.casehub.blocks.agentic.pattern`

**Kind:** `class`

## Fields

### `rootTask` (`TaskNode<T>`)

## Constructors

### `public HtnBuilder()`

## Methods

### `public io.casehub.blocks.agentic.pattern.HtnBuilder<T> agents(io.casehub.blocks.agentic.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.blocks.agentic.AgentRef[]`)

### `public io.casehub.blocks.agentic.pattern.HtnBuilder<T> agents(io.casehub.blocks.agentic.RoutingCandidate[] candidates)`

#### Parameters

- `candidates` (`io.casehub.blocks.agentic.RoutingCandidate[]`)

### `public Uni<io.casehub.blocks.agentic.model.ExecutionResult> execute(T initialContext)`

#### Parameters

- `initialContext` (`T`)

### `private Uni<DagPlan<TaskNode.LeafTask<T>>> flatten(TaskNode<T> node, T state)`

#### Parameters

- `node` (`TaskNode<T>`)
- `state` (`T`)

### `public io.casehub.blocks.agentic.pattern.HtnBuilder<T> rootTask(TaskNode<T> rootTask)`

#### Parameters

- `rootTask` (`TaskNode<T>`)
