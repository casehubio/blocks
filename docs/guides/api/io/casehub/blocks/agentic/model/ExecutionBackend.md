# io.casehub.blocks.agentic.model.ExecutionBackend

**Package:** `io.casehub.blocks.agentic.model`

**Kind:** `interface`

## Methods

### `public abstract Uni<io.casehub.blocks.agentic.model.ExecutionResult> execute(io.casehub.blocks.agentic.model.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `initialContext` (`T`)

### `public static io.casehub.blocks.agentic.model.ExecutionBackend<T> orchestrated()`

### `public static io.casehub.blocks.agentic.model.ExecutionBackend<T> orchestrated(io.casehub.blocks.agentic.model.AgentInvoker<T> invoker)`

#### Parameters

- `invoker` (`io.casehub.blocks.agentic.model.AgentInvoker<T>`)
