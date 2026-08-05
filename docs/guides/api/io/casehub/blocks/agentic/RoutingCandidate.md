# io.casehub.blocks.agentic.RoutingCandidate

**Package:** `io.casehub.blocks.agentic`

**Kind:** `record`

A candidate for agent routing — pairs a dispatch reference with an optional identity profile.

## Fields

### `descriptor` (`AgentDescriptor`)

### `ref` (`io.casehub.blocks.agentic.AgentRef`)

## Record Components

### `descriptor` (`AgentDescriptor`)

the agent's identity profile for routing metadata.
                  Nullable — pattern builders that accept bare `AgentRef`
                  create candidates without descriptors. Routing strategies
                  must handle null descriptors gracefully.

### `ref` (`io.casehub.blocks.agentic.AgentRef`)

the agent dispatch reference — never null

## Constructors

### `public RoutingCandidate(io.casehub.blocks.agentic.AgentRef ref, AgentDescriptor descriptor)`

#### Parameters

- `ref` (`io.casehub.blocks.agentic.AgentRef`)
- `descriptor` (`AgentDescriptor`)

## Methods

### `public AgentDescriptor descriptor()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.blocks.agentic.AgentRef ref()`

### `public final java.lang.String toString()`
