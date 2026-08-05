# io.casehub.blocks.agentic.termination.JudgeConvergence

**Package:** `io.casehub.blocks.agentic.termination`

**Kind:** `class`

Termination condition that delegates convergence judgment to an external agent.

<p>The judge receives all accumulated debate results as `List<AgentResult>`
and returns an `AgentResult`. The convergence predicate maps that result
to continue/converge — by default, SUCCESS
means converged, anything else means continue.

<p>A safety cap at `maxIterations` forces termination even if the judge
never reports convergence.

## Fields

### `convergencePredicate` (`java.util.function.Predicate<io.casehub.blocks.agentic.AgentResult>`)

### `invoker` (`io.casehub.blocks.agentic.model.AgentInvoker<java.util.List<io.casehub.blocks.agentic.AgentResult>>`)

### `judge` (`io.casehub.blocks.agentic.AgentRef`)

### `maxIterations` (`int`)

## Constructors

### `public JudgeConvergence(io.casehub.blocks.agentic.AgentRef judge, int maxIterations)`

Convenience constructor using the default invoker and default convergence predicate
(SUCCESS = converged, anything else = continue).

#### Parameters

- `judge` (`io.casehub.blocks.agentic.AgentRef`) — must be an `AgentRef.ExternalAgent` — the default invoker only
             supports ExternalAgent
- `maxIterations` (`int`) — safety cap

#### Throws

- `IllegalArgumentException` — if judge is not an ExternalAgent

### `public JudgeConvergence(io.casehub.blocks.agentic.AgentRef judge, io.casehub.blocks.agentic.model.AgentInvoker<java.util.List<io.casehub.blocks.agentic.AgentResult>> invoker, int maxIterations, java.util.function.Predicate<io.casehub.blocks.agentic.AgentResult> convergencePredicate)`

Full constructor with explicit invoker and convergence predicate.

#### Parameters

- `judge` (`io.casehub.blocks.agentic.AgentRef`)
- `invoker` (`io.casehub.blocks.agentic.model.AgentInvoker<java.util.List<io.casehub.blocks.agentic.AgentResult>>`)
- `maxIterations` (`int`)
- `convergencePredicate` (`java.util.function.Predicate<io.casehub.blocks.agentic.AgentResult>`)

## Methods

### `public Uni<io.casehub.blocks.agentic.termination.TerminationDecision> evaluate(io.casehub.blocks.agentic.termination.TerminationContext<T> context)`

#### Parameters

- `context` (`io.casehub.blocks.agentic.termination.TerminationContext<T>`)
