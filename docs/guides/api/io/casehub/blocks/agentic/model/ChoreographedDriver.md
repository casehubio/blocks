# io.casehub.blocks.agentic.model.ChoreographedDriver

**Package:** `io.casehub.blocks.agentic.model`

**Kind:** `class`

Event-driven reactive driver that composes the five agentic SPIs into an
event-reactive execution cycle: route -> activate -> dispatch -> aggregate -> terminate.

<p>Structurally similar to `OrchestratedDriver`, but operates in a reactive mode
where agents are activated based on events rather than imperative loops. For the initial
implementation, the "event" is the completion of the previous cycle — full event-bus
integration (Vert.x EventBus, qhorus channel observation) is deferred to engine integration.

<p>Key behavioral differences from OrchestratedDriver:
<ul>
  <li>Starts in `ExecutionState.WaitingForEvent` rather than `ExecutionState.Running`</li>
  <li>Transitions back to `ExecutionState.WaitingForEvent` between cycles</li>
  <li>Conceptually event-reactive: agents fire when their activation conditions are met by external events</li>
</ul>

<p>Agent invocation currently supports `AgentRef.ExternalAgent` via its
completion-stage function. Other AgentRef variants return a failure result —
runtime dispatch integration is deferred to a later task.

## Constructors

### `public ChoreographedDriver()`

### `public ChoreographedDriver(io.casehub.blocks.agentic.model.AgentInvoker<T> invoker)`

#### Parameters

- `invoker` (`io.casehub.blocks.agentic.model.AgentInvoker<T>`)

## Methods

### `protected Uni<io.casehub.blocks.agentic.model.ExecutionResult> runLoop(io.casehub.blocks.agentic.model.ExecutionModel<T> model, T context)`

#### Parameters

- `model` (`io.casehub.blocks.agentic.model.ExecutionModel<T>`)
- `context` (`T`)
