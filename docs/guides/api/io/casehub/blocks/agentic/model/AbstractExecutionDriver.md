# io.casehub.blocks.agentic.model.AbstractExecutionDriver

**Package:** `io.casehub.blocks.agentic.model`

**Kind:** `class`

Abstract base for execution drivers that composes the five agentic SPIs.
Extracts the common five-phase loop: route -> activate -> dispatch -> aggregate -> terminate.

<p>Tracks per-agent activation state across iterations and populates `ActivationContext`
with tracked data instead of hardcoded zeros. Calls `onExecutionStart` and
`onExecutionComplete` lifecycle hooks.

<p>Subclasses implement Object) to control iteration
vs. event-wait semantics.

## Fields

### `LOG` (`java.lang.System.Logger`)

### `activationCounts` (`java.util.Map<io.casehub.blocks.agentic.AgentRef,java.lang.Integer>`)

### `cancelled` (`boolean`)

### `consecutiveIdleCounts` (`java.util.Map<io.casehub.blocks.agentic.AgentRef,java.lang.Integer>`)

### `currentState` (`java.util.concurrent.atomic.AtomicReference<io.casehub.blocks.agentic.model.ExecutionState>`)

### `executionStart` (`java.time.Instant`)

### `invoker` (`io.casehub.blocks.agentic.model.AgentInvoker<T>`)

### `iterationCount` (`int`)

### `lastAggregationResult` (`io.casehub.blocks.agentic.aggregation.AggregationResult`)

## Constructors

### `protected AbstractExecutionDriver()`

### `protected AbstractExecutionDriver(io.casehub.blocks.agentic.model.AgentInvoker<T> invoker)`

#### Parameters

- `invoker` (`io.casehub.blocks.agentic.model.AgentInvoker<T>`)

## Methods

### `public Uni<java.lang.Void> cancel()`

### `protected java.util.List<io.casehub.blocks.agentic.AgentResult> dispatchAgents(io.casehub.blocks.agentic.model.ExecutionModel<T> model, java.util.List<io.casehub.blocks.agentic.AgentRef> agents, T context)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `agents` (`java.util.List<io.casehub.blocks.agentic.AgentRef>`)
- `context` (`T`)

### `public final Uni<io.casehub.blocks.agentic.model.ExecutionResult> execute(io.casehub.blocks.agentic.model.ExecutionModel<T> model, T initialContext)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `initialContext` (`T`)

### `protected io.casehub.blocks.agentic.model.ExecutionResult executeIteration(io.casehub.blocks.agentic.model.ExecutionModel<T> model, T context, int iteration, java.time.Instant start, java.util.List<io.casehub.blocks.agentic.AgentResult> allResults)`

Executes one iteration of the five-phase loop. Returns null for Continue,
otherwise returns the terminal ExecutionResult.

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `context` (`T`)
- `iteration` (`int`)
- `start` (`java.time.Instant`)
- `allResults` (`java.util.List<io.casehub.blocks.agentic.AgentResult>`)

### `protected io.casehub.blocks.agentic.AgentResult invokeAgent(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.AgentRef agent, T context)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `context` (`T`)

### `protected boolean isCancelled()`

### `protected void notifyActivation(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.AgentRef agent, boolean activated)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `activated` (`boolean`)

### `protected void notifyAgentDispatched(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.AgentRef agent)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)

### `protected void notifyAgentResult(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.AgentResult result)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `result` (`io.casehub.blocks.agentic.AgentResult`)

### `protected void notifyAggregation(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.aggregation.AggregationResult result)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `result` (`io.casehub.blocks.agentic.aggregation.AggregationResult`)

### `protected void notifyExecutionComplete(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.model.ExecutionResult result)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `result` (`io.casehub.blocks.agentic.model.ExecutionResult`)

### `protected void notifyExecutionStart(io.casehub.blocks.agentic.model.ExecutionModel<T> model)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)

### `protected void notifyFailure(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.AgentRef agent, java.lang.Throwable cause)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `agent` (`io.casehub.blocks.agentic.AgentRef`)
- `cause` (`java.lang.Throwable`)

### `protected void notifyRoutingDecision(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.routing.RoutingDecision decision, java.util.List<io.casehub.blocks.agentic.RoutingCandidate> candidates)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `decision` (`io.casehub.blocks.agentic.routing.RoutingDecision`)
- `candidates` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `protected void notifyTermination(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.termination.TerminationDecision decision)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `decision` (`io.casehub.blocks.agentic.termination.TerminationDecision`)

### `protected abstract Uni<io.casehub.blocks.agentic.model.ExecutionResult> runLoop(io.casehub.blocks.agentic.model.ExecutionModel<T> model, T context)`

Subclass-specific loop structure. OrchestratedDriver uses a while loop;
ChoreographedDriver transitions to WaitingForEvent between iterations.

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `context` (`T`)

### `public io.casehub.blocks.agentic.model.ExecutionState state()`

### `protected void transition(io.casehub.blocks.agentic.model.ExecutionModel<T> model, io.casehub.blocks.agentic.model.ExecutionState newState)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `newState` (`io.casehub.blocks.agentic.model.ExecutionState`)
