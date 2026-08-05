# io.casehub.blocks.agentic.pattern.SupervisorBuilder

**Package:** `io.casehub.blocks.agentic.pattern`

**Kind:** `class`

## Fields

### `agentProvider` (`AgentProvider`)

### `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

## Constructors

### `public SupervisorBuilder()`

### `public SupervisorBuilder(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

## Methods

### `public io.casehub.blocks.agentic.pattern.SupervisorBuilder<T> agents(io.casehub.blocks.agentic.AgentRef[] agents)`

#### Parameters

- `agents` (`io.casehub.blocks.agentic.AgentRef[]`)

### `public io.casehub.blocks.agentic.pattern.SupervisorBuilder<T> agents(io.casehub.blocks.agentic.RoutingCandidate[] candidates)`

#### Parameters

- `candidates` (`io.casehub.blocks.agentic.RoutingCandidate[]`)

### `public io.casehub.blocks.agentic.pattern.SupervisorBuilder<T> stateRenderer(java.util.function.Function<T,java.lang.String> stateRenderer)`

Sets a state renderer that converts typed state `T` to a String
representation for the LLM routing prompt. Eagerly applies — if an
`AgentProvider` is available, immediately replaces the routing
strategy with a new `LlmSelectedRouting` using this renderer.

<p>Last-writer-wins with `.route`: calling `stateRenderer()`
after `route()` replaces the custom routing with LLM routing;
calling `route()` after `stateRenderer()` replaces LLM
routing with the custom strategy.

#### Parameters

- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)
