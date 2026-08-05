# io.casehub.blocks.routing.agent.PlanCompositionAnalyser

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

`RoutingSignalProvider` that scores candidates based on case-level outcomes in multi-step
plans.

<p>For each retrieved experience with `planTrace.size() >= 2`, this analyser examines
which eligible candidates appeared in multi-step plans and weights their contribution by the
case-level outcome (COMPLETED, FAULTED, CANCELLED) and the experience's similarity score.

<p>Returns `null` when no multi-step plan data exists or no eligible candidates appear
in any trace.

## Fields

### `caseOutcomeWeights` (`io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights`)

## Constructors

### `public PlanCompositionAnalyser(Instance<io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights> caseOutcomeWeights)`

#### Parameters

- `caseOutcomeWeights` (`Instance<io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights>`)

## Methods

### `public RoutingSignal evaluate(AgentRoutingContext context, java.util.List<AgentCandidate> eligible)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligible` (`java.util.List<AgentCandidate>`)

### `public java.lang.String id()`
