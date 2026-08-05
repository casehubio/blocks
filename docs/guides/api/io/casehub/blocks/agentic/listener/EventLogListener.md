# io.casehub.blocks.agentic.listener.EventLogListener

**Package:** `io.casehub.blocks.agentic.listener`

**Kind:** `class`

Persists orchestration events to the engine's EventLog via a consumer-provided sink.
Records all events for operational observability.

<p>Sink implementations must be non-blocking — use in-memory buffers or async
queues. The sink is called synchronously from the driver loop; blocking I/O
in the sink stalls the execution.

## Fields

### `sink` (`io.casehub.blocks.agentic.listener.EventLogListener.EventSink`)

## Constructors

### `public EventLogListener(io.casehub.blocks.agentic.listener.EventLogListener.EventSink sink)`

#### Parameters

- `sink` (`io.casehub.blocks.agentic.listener.EventLogListener.EventSink`)

## Methods

### `public void onActivation(io.casehub.blocks.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `activated` (`boolean`)

### `public void onAgentDispatched(io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `public void onAgentResult(io.casehub.blocks.agentic.AgentResult result)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.AgentResult`)

### `public void onAggregation(io.casehub.blocks.agentic.aggregation.AggregationResult result)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.aggregation.AggregationResult`)

### `public void onExecutionComplete(io.casehub.blocks.agentic.model.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.model.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public void onExecutionStart(io.casehub.blocks.agentic.model.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<?>`)

### `public void onFailure(io.casehub.blocks.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public void onRoutingDecision(io.casehub.blocks.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.blocks.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `public void onTermination(io.casehub.blocks.agentic.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.blocks.agentic.termination.TerminationDecision`)

### `private static java.lang.String truncate(java.lang.String s, int max)`

#### Parameters

- `s` (`java.lang.String`)
- `max` (`int`)
