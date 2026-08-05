# io.casehub.blocks.agentic.routing.FirstMatchRouting

**Package:** `io.casehub.blocks.agentic.routing`

**Kind:** `class`

## Fields

### `matcher` (`java.util.function.Predicate<io.casehub.blocks.agentic.RoutingCandidate>`)

## Constructors

### `public FirstMatchRouting(java.util.function.Predicate<io.casehub.blocks.agentic.RoutingCandidate> matcher)`

#### Parameters

- `matcher` (`java.util.function.Predicate<io.casehub.blocks.agentic.RoutingCandidate>`)

## Methods

### `public Uni<io.casehub.blocks.agentic.routing.RoutingDecision> route(io.casehub.blocks.agentic.routing.RoutingContext<T> context)`

#### Parameters

- `context` (`io.casehub.blocks.agentic.routing.RoutingContext<T>`)
