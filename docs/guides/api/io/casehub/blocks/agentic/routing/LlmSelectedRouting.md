# io.casehub.blocks.agentic.routing.LlmSelectedRouting

**Package:** `io.casehub.blocks.agentic.routing`

**Kind:** `class`

LLM-driven agent selection — asks a language model to pick the best agent
from a list of candidates for a given task.

<p>The type parameter `T` flows typed through the entire execution
pipeline. The `stateRenderer` converts `T` to `String`
only at the LLM API boundary, preserving type safety everywhere else.

## Fields

### `LOG` (`java.lang.System.Logger`)

### `SYSTEM_PROMPT` (`java.lang.String`)

### `agentProvider` (`AgentProvider`)

### `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

## Constructors

### `public LlmSelectedRouting(AgentProvider agentProvider)`

Convenience constructor — defaults stateRenderer to `Object::toString`.

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public LlmSelectedRouting(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer)`

Creates an LLM-selected routing strategy.

#### Parameters

- `agentProvider` (`AgentProvider`) — the LLM provider for agent selection
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`) — converts typed state `T` to a String representation
                     for the LLM prompt — the only point where T is converted
                     to String

## Methods

### `private java.lang.String buildAgentCard(io.casehub.blocks.agentic.RoutingCandidate candidate, int index)`

#### Parameters

- `candidate` (`io.casehub.blocks.agentic.RoutingCandidate`)
- `index` (`int`)

### `private java.lang.String buildUserPrompt(io.casehub.blocks.agentic.routing.RoutingContext<T> context)`

#### Parameters

- `context` (`io.casehub.blocks.agentic.routing.RoutingContext<T>`)

### `static java.lang.String candidateName(io.casehub.blocks.agentic.RoutingCandidate candidate, int index)`

#### Parameters

- `candidate` (`io.casehub.blocks.agentic.RoutingCandidate`)
- `index` (`int`)

### `private static java.lang.String extractAgentName(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)

### `static java.lang.String extractReason(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)

### `private io.casehub.blocks.agentic.routing.RoutingDecision parseSelection(java.lang.String text, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> candidates)`

#### Parameters

- `text` (`java.lang.String`)
- `candidates` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `public Uni<io.casehub.blocks.agentic.routing.RoutingDecision> route(io.casehub.blocks.agentic.routing.RoutingContext<T> context)`

#### Parameters

- `context` (`io.casehub.blocks.agentic.routing.RoutingContext<T>`)
