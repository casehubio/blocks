# io.casehub.blocks.routing.agent.CoordinationSignalProvider

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

## Fields

### `outcomeWeights` (`io.casehub.blocks.routing.agent.CoordinationOutcomeWeights`)

## Constructors

### `public CoordinationSignalProvider(io.casehub.blocks.routing.agent.CoordinationOutcomeWeights outcomeWeights)`

#### Parameters

- `outcomeWeights` (`io.casehub.blocks.routing.agent.CoordinationOutcomeWeights`)

## Methods

### `public RoutingSignal evaluate(AgentRoutingContext context, java.util.List<AgentCandidate> eligible)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligible` (`java.util.List<AgentCandidate>`)

### `static java.util.Set<java.lang.String> extractTeam(java.util.List<ExperiencePlanStep> planTrace)`

#### Parameters

- `planTrace` (`java.util.List<ExperiencePlanStep>`)

### `public java.lang.String id()`
