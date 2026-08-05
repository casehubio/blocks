# io.casehub.blocks.routing.agent.DispositionAwareRouting

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

## Fields

### `CONTEXT_KEY` (`java.lang.String`)

### `DISPOSITION_KEY` (`java.lang.String`)

## Constructors

### `public DispositionAwareRouting()`

## Methods

### `public RoutingSignal evaluate(AgentRoutingContext context, java.util.List<AgentCandidate> eligible)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligible` (`java.util.List<AgentCandidate>`)

### `static io.casehub.blocks.routing.agent.DispositionProfile extractProfile(JsonNode caseContext, java.lang.String capabilityName)`

#### Parameters

- `caseContext` (`JsonNode`)
- `capabilityName` (`java.lang.String`)

### `public java.lang.String id()`

### `private static io.casehub.blocks.routing.agent.DispositionProfile parseProfile(JsonNode node)`

#### Parameters

- `node` (`JsonNode`)

### `static double score(io.casehub.blocks.routing.agent.DispositionProfile profile, AgentDisposition disposition)`

#### Parameters

- `profile` (`io.casehub.blocks.routing.agent.DispositionProfile`)
- `disposition` (`AgentDisposition`)
