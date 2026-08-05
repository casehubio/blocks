# io.casehub.blocks.agentic.routing.Routing

**Package:** `io.casehub.blocks.agentic.routing`

**Kind:** `class`

## Constructors

### `private Routing()`

## Methods

### `public static io.casehub.blocks.agentic.routing.FirstMatchRouting<T> firstMatch(java.util.function.Predicate<io.casehub.blocks.agentic.RoutingCandidate> matcher)`

#### Parameters

- `matcher` (`java.util.function.Predicate<io.casehub.blocks.agentic.RoutingCandidate>`)

### `public static io.casehub.blocks.agentic.routing.LlmSelectedRouting<T> llmSelected(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public static io.casehub.blocks.agentic.routing.RoundRobinRouting<T> roundRobin()`

### `public static io.casehub.blocks.agentic.routing.SequentialRouting<T> sequential()`
