# io.casehub.blocks.agentic.activation.ActivationContext

**Package:** `io.casehub.blocks.agentic.activation`

**Kind:** `record`

## Fields

### `activationCount` (`int`)

### `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `consecutiveIdleActivations` (`int`)

### `event` (`java.lang.Object`)

### `lastAggregationResult` (`java.util.Optional<io.casehub.blocks.agentic.aggregation.AggregationResult>`)

### `state` (`T`)

## Record Components

### `activationCount` (`int`)

### `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `consecutiveIdleActivations` (`int`)

### `event` (`java.lang.Object`)

### `lastAggregationResult` (`java.util.Optional<io.casehub.blocks.agentic.aggregation.AggregationResult>`)

### `state` (`T`)

## Constructors

### `public ActivationContext(java.lang.Object event, T state, io.casehub.blocks.agentic.AgentRef agent, int activationCount, java.util.Optional<io.casehub.blocks.agentic.aggregation.AggregationResult> lastAggregationResult, int consecutiveIdleActivations)`

#### Parameters

- `event` (`java.lang.Object`)
- `state` (`T`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `activationCount` (`int`)
- `lastAggregationResult` (`java.util.Optional<io.casehub.blocks.agentic.aggregation.AggregationResult>`)
- `consecutiveIdleActivations` (`int`)

## Methods

### `public int activationCount()`

### `public io.casehub.blocks.agentic.AgentRef agent()`

### `public int consecutiveIdleActivations()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.Object event()`

### `public final int hashCode()`

### `public java.util.Optional<io.casehub.blocks.agentic.aggregation.AggregationResult> lastAggregationResult()`

### `public T state()`

### `public final java.lang.String toString()`
