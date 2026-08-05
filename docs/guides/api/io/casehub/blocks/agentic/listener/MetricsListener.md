# io.casehub.blocks.agentic.listener.MetricsListener

**Package:** `io.casehub.blocks.agentic.listener`

**Kind:** `class`

## Fields

### `ACTIVATED` (`AttributeKey<java.lang.String>`)

### `AGENT` (`AttributeKey<java.lang.String>`)

### `DECISION_TYPE` (`AttributeKey<java.lang.String>`)

### `OUTCOME` (`AttributeKey<java.lang.String>`)

### `STATUS` (`AttributeKey<java.lang.String>`)

### `activationEvaluations` (`LongCounter`)

### `agentDuration` (`DoubleHistogram`)

### `agentFailures` (`LongCounter`)

### `executionDuration` (`DoubleHistogram`)

### `executionIterations` (`DoubleHistogram`)

### `routingDecisions` (`LongCounter`)

## Constructors

### `public MetricsListener(Meter meter)`

#### Parameters

- `meter` (`Meter`)

## Methods

### `public void onActivation(io.casehub.blocks.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `activated` (`boolean`)

### `public void onAgentResult(io.casehub.blocks.agentic.AgentResult result)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.AgentResult`)

### `public void onExecutionComplete(io.casehub.blocks.agentic.model.ExecutionResult result, java.time.Duration executionDuration, int iterationCount)`

#### Parameters

- `result` (`io.casehub.blocks.agentic.model.ExecutionResult`)
- `executionDuration` (`java.time.Duration`)
- `iterationCount` (`int`)

### `public void onFailure(io.casehub.blocks.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `public void onRoutingDecision(io.casehub.blocks.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> candidates)`

#### Parameters

- `decision` (`io.casehub.blocks.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)
