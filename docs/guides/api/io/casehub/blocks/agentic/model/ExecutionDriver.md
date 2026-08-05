# io.casehub.blocks.agentic.model.ExecutionDriver

**Package:** `io.casehub.blocks.agentic.model`

**Kind:** `interface`

## Methods

### `public abstract Uni<java.lang.Void> cancel()`

### `public abstract Uni<io.casehub.blocks.agentic.model.ExecutionResult> execute(io.casehub.blocks.agentic.model.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `initialContext` (`T`)

### `public abstract io.casehub.blocks.agentic.model.ExecutionState state()`
