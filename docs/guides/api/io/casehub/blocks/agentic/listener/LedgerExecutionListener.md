# io.casehub.blocks.agentic.listener.LedgerExecutionListener

**Package:** `io.casehub.blocks.agentic.listener`

**Kind:** `class`

Persists compliance-grade audit entries via a consumer-provided sink.
Each entry carries actor identity and role for EU AI Act Art.12 regulatory traceability.

<p>Records the full decision-dispatch-result chain: routing decisions with LLM reasoning,
activation evaluations, agent dispatches and results, termination decisions,
escalation events, and failures. This chain closes the Art.12 audit gap — you know
what was decided (routing), what was activated, what was dispatched, what each agent
returned, and why execution terminated.

<p>Sink implementations must be non-blocking — use in-memory buffers or async
queues. The sink is called synchronously from the driver loop; blocking I/O
in the sink stalls the execution.

## Fields

### `sink` (`io.casehub.blocks.agentic.listener.LedgerExecutionListener.LedgerSink`)

### `supervisorActorId` (`java.lang.String`)

## Constructors

### `public LedgerExecutionListener(io.casehub.blocks.agentic.listener.LedgerExecutionListener.LedgerSink sink, java.lang.String supervisorActorId)`

#### Parameters

- `sink` (`io.casehub.blocks.agentic.listener.LedgerExecutionListener.LedgerSink`)
- `supervisorActorId` (`java.lang.String`)

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
