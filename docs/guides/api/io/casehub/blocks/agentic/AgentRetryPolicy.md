# io.casehub.blocks.agentic.FailurePolicy.AgentRetryPolicy

**Package:** `io.casehub.blocks.agentic`

**Kind:** `record`

## Fields

### `backoff` (`java.time.Duration`)

### `backoffStrategy` (`io.casehub.blocks.agentic.FailurePolicy.BackoffStrategy`)

### `maxRetries` (`int`)

### `onExhausted` (`io.casehub.blocks.agentic.FailurePolicy.AgentFailureAction`)

## Record Components

### `backoff` (`java.time.Duration`)

### `backoffStrategy` (`io.casehub.blocks.agentic.FailurePolicy.BackoffStrategy`)

### `maxRetries` (`int`)

### `onExhausted` (`io.casehub.blocks.agentic.FailurePolicy.AgentFailureAction`)

## Constructors

### `public AgentRetryPolicy(int maxRetries, java.time.Duration backoff, io.casehub.blocks.agentic.FailurePolicy.BackoffStrategy backoffStrategy, io.casehub.blocks.agentic.FailurePolicy.AgentFailureAction onExhausted)`

#### Parameters

- `maxRetries` (`int`)
- `backoff` (`java.time.Duration`)
- `backoffStrategy` (`io.casehub.blocks.agentic.FailurePolicy.BackoffStrategy`)
- `onExhausted` (`io.casehub.blocks.agentic.FailurePolicy.AgentFailureAction`)

## Methods

### `public java.time.Duration backoff()`

### `public io.casehub.blocks.agentic.FailurePolicy.BackoffStrategy backoffStrategy()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int maxRetries()`

### `public io.casehub.blocks.agentic.FailurePolicy.AgentFailureAction onExhausted()`

### `public final java.lang.String toString()`
