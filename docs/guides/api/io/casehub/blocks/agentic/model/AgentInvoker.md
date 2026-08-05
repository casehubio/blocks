# io.casehub.blocks.agentic.model.AgentInvoker

**Package:** `io.casehub.blocks.agentic.model`

**Kind:** `interface`

## Methods

### `public static io.casehub.blocks.agentic.model.AgentInvoker<T> defaultInvoker()`

### `public abstract Uni<io.casehub.blocks.agentic.AgentResult> invoke(io.casehub.blocks.agentic.AgentRef agent, T state)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `state` (`T`)

### `private static io.casehub.blocks.agentic.AgentResult invokeComposed(io.casehub.blocks.agentic.AgentRef agent, io.casehub.blocks.agentic.AgentRef.ComposedAgent composed, T state, java.time.Instant start)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `composed` (`io.casehub.blocks.agentic.AgentRef.ComposedAgent`)
- `state` (`T`)
- `start` (`java.time.Instant`)

### `public default io.casehub.blocks.agentic.model.AgentInvoker<T> withFallback(io.casehub.blocks.agentic.model.AgentInvoker<T> fallback)`

#### Parameters

- `fallback` (`io.casehub.blocks.agentic.model.AgentInvoker<T>`)
