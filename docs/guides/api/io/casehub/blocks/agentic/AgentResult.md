# io.casehub.blocks.agentic.AgentResult

**Package:** `io.casehub.blocks.agentic`

**Kind:** `record`

## Fields

### `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `duration` (`java.time.Duration`)

### `output` (`java.lang.Object`)

### `status` (`io.casehub.blocks.agentic.AgentResult.AgentResultStatus`)

## Record Components

### `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `duration` (`java.time.Duration`)

### `output` (`java.lang.Object`)

### `status` (`io.casehub.blocks.agentic.AgentResult.AgentResultStatus`)

## Constructors

### `public AgentResult(io.casehub.blocks.agentic.AgentRef agent, java.lang.Object output, java.time.Duration duration, io.casehub.blocks.agentic.AgentResult.AgentResultStatus status)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `output` (`java.lang.Object`)
- `duration` (`java.time.Duration`)
- `status` (`io.casehub.blocks.agentic.AgentResult.AgentResultStatus`)

## Methods

### `public io.casehub.blocks.agentic.AgentRef agent()`

### `public static io.casehub.blocks.agentic.AgentResult declined(io.casehub.blocks.agentic.AgentRef agent, java.lang.Object reason)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `reason` (`java.lang.Object`)

### `public java.time.Duration duration()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.blocks.agentic.AgentResult failure(io.casehub.blocks.agentic.AgentRef agent, java.lang.Object output)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `output` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Object output()`

### `public io.casehub.blocks.agentic.AgentResult.AgentResultStatus status()`

### `public static io.casehub.blocks.agentic.AgentResult success(io.casehub.blocks.agentic.AgentRef agent, java.lang.Object output)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `output` (`java.lang.Object`)

### `public static io.casehub.blocks.agentic.AgentResult timeout(io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `public final java.lang.String toString()`
