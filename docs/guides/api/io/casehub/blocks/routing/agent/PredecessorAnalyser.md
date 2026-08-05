# io.casehub.blocks.routing.agent.PredecessorAnalyser

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

`RoutingSignalProvider` that scores candidates based on immediate predecessor context
in historical plan traces.

<p>For each retrieved experience with `planTrace.size() >= 2`, this analyser sorts steps
by priority, finds steps matching the target capability, and scores each eligible candidate
based on the case outcome weighted by similarity — recording the immediate predecessor's
(capability, worker) pair for observability.

## Fields

### `caseOutcomeWeights` (`io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights`)

## Constructors

### `public PredecessorAnalyser(Instance<io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights> caseOutcomeWeights)`

#### Parameters

- `caseOutcomeWeights` (`Instance<io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights>`)

## Methods

### `public RoutingSignal evaluate(AgentRoutingContext context, java.util.List<AgentCandidate> eligible)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligible` (`java.util.List<AgentCandidate>`)

### `public java.lang.String id()`
