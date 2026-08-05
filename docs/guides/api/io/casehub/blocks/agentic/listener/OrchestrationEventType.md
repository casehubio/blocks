# io.casehub.blocks.agentic.listener.OrchestrationEventType

**Package:** `io.casehub.blocks.agentic.listener`

**Kind:** `enum`

Orchestration-level event types produced by blocks' execution drivers.
Maps to `CaseHubEventType` at the EventLog sink — the mapping is owned
by the sink implementation, not by blocks. When engine#626 adds orchestration
types to `CaseHubEventType`, the sink maps directly.

## Enum Constants

### `ACTIVATION_EVALUATED` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `AGENT_DISPATCHED` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `AGENT_FAILED` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `AGENT_RESULT` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `AGGREGATION_COMPLETED` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `EXECUTION_COMPLETED` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `EXECUTION_STARTED` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `ROUTING_DECISION` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

### `TERMINATION_EVALUATED` (`io.casehub.blocks.agentic.listener.OrchestrationEventType`)

## Constructors

### `private OrchestrationEventType()`

## Methods

### `public static io.casehub.blocks.agentic.listener.OrchestrationEventType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.blocks.agentic.listener.OrchestrationEventType[] values()`
