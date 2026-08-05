# io.casehub.blocks.agentic.model.ExecutionEventListener

**Package:** `io.casehub.blocks.agentic.model`

**Kind:** `interface`

## Methods

### `public static java.lang.String agentName(io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `public default void onActivation(io.casehub.blocks.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `activated` (`boolean`)

### `public default void onAgentDispatched(io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `public default void onAgentResult(io.casehub.blocks.agentic.AgentResult result)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.AgentResult`)

### `public default void onAggregation(io.casehub.blocks.agentic.aggregation.AggregationResult result)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.aggregation.AggregationResult`)

### `public default void onExecutionComplete(io.casehub.blocks.agentic.model.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.model.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public default void onExecutionStart(io.casehub.blocks.agentic.model.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<?>`)

### `public default void onFailure(io.casehub.blocks.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public default void onRoutingDecision(io.casehub.blocks.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.blocks.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `public default void onStateTransition(io.casehub.blocks.agentic.model.ExecutionState from, io.casehub.blocks.agentic.model.ExecutionState to)`

#### Parameters

- `from` (`io.casehub.blocks.agentic.model.ExecutionState`)
- `to` (`io.casehub.blocks.agentic.model.ExecutionState`)

### `public default void onTermination(io.casehub.blocks.agentic.termination.TerminationDecision decision)`

#### Parameters

- `decision` (`io.casehub.blocks.agentic.termination.TerminationDecision`)
