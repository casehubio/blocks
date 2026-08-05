# io.casehub.blocks.agentic.FailurePolicy

**Package:** `io.casehub.blocks.agentic`

**Kind:** `record`

## Fields

### `agentRetry` (`io.casehub.blocks.agentic.FailurePolicy.AgentRetryPolicy`)

### `onDeadlock` (`io.casehub.blocks.agentic.FailurePolicy.AggregationFailureAction`)

### `onRoutingFailure` (`io.casehub.blocks.agentic.FailurePolicy.RoutingFailureAction`)

## Record Components

### `agentRetry` (`io.casehub.blocks.agentic.FailurePolicy.AgentRetryPolicy`)

### `onDeadlock` (`io.casehub.blocks.agentic.FailurePolicy.AggregationFailureAction`)

### `onRoutingFailure` (`io.casehub.blocks.agentic.FailurePolicy.RoutingFailureAction`)

## Constructors

### `public FailurePolicy(io.casehub.blocks.agentic.FailurePolicy.RoutingFailureAction onRoutingFailure, io.casehub.blocks.agentic.FailurePolicy.AggregationFailureAction onDeadlock, io.casehub.blocks.agentic.FailurePolicy.AgentRetryPolicy agentRetry)`

#### Parameters

- `onRoutingFailure` (`io.casehub.blocks.agentic.FailurePolicy.RoutingFailureAction`)
- `onDeadlock` (`io.casehub.blocks.agentic.FailurePolicy.AggregationFailureAction`)
- `agentRetry` (`io.casehub.blocks.agentic.FailurePolicy.AgentRetryPolicy`)

## Methods

### `public io.casehub.blocks.agentic.FailurePolicy.AgentRetryPolicy agentRetry()`

### `public static io.casehub.blocks.agentic.FailurePolicy defaults()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.blocks.agentic.FailurePolicy.AggregationFailureAction onDeadlock()`

### `public io.casehub.blocks.agentic.FailurePolicy.RoutingFailureAction onRoutingFailure()`

### `public final java.lang.String toString()`
