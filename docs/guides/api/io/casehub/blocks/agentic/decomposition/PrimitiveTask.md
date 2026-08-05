# io.casehub.blocks.agentic.decomposition.PrimitiveTask

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `record`

## Fields

### `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `createdAt` (`java.time.Instant`)

### `description` (`java.lang.String`)

### `effect` (`java.util.function.Consumer<T>`)

### `id` (`java.lang.String`)

### `outputContract` (`io.casehub.blocks.agentic.decomposition.OutputContract`)

### `precondition` (`java.util.function.Predicate<T>`)

## Record Components

### `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `createdAt` (`java.time.Instant`)

### `description` (`java.lang.String`)

### `effect` (`java.util.function.Consumer<T>`)

### `id` (`java.lang.String`)

### `outputContract` (`io.casehub.blocks.agentic.decomposition.OutputContract`)

### `precondition` (`java.util.function.Predicate<T>`)

## Constructors

### `public PrimitiveTask(java.lang.String id, java.time.Instant createdAt, java.lang.String description, io.casehub.blocks.agentic.AgentRef agent, java.util.function.Predicate<T> precondition, java.util.function.Consumer<T> effect)`

#### Parameters

- `id` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `description` (`java.lang.String`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `precondition` (`java.util.function.Predicate<T>`)
- `effect` (`java.util.function.Consumer<T>`)

### `public PrimitiveTask(java.lang.String id, java.time.Instant createdAt, java.lang.String description, io.casehub.blocks.agentic.AgentRef agent, java.util.function.Predicate<T> precondition, java.util.function.Consumer<T> effect, io.casehub.blocks.agentic.decomposition.OutputContract outputContract)`

#### Parameters

- `id` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `description` (`java.lang.String`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `precondition` (`java.util.function.Predicate<T>`)
- `effect` (`java.util.function.Consumer<T>`)
- `outputContract` (`io.casehub.blocks.agentic.decomposition.OutputContract`)

## Methods

### `public io.casehub.blocks.agentic.AgentRef agent()`

### `public java.time.Instant createdAt()`

### `public java.lang.String description()`

### `public java.util.function.Consumer<T> effect()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public ExecutorRef executor()`

### `public final int hashCode()`

### `public java.lang.String id()`

### `public io.casehub.blocks.agentic.decomposition.OutputContract outputContract()`

### `public java.util.function.Predicate<T> precondition()`

### `public TaskStatus status()`

### `public final java.lang.String toString()`
