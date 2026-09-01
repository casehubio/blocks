---
title: "The Goal That Earns Its Rank"
date: 2026-09-01
entry_type: note
subtype: diary
series: casehub-blocks
projects: casehub-blocks
tags: [goal-governance, narrative-identity, escalation, cross-axis, layer-3]
---

# The Goal That Earns Its Rank

Layer 2 of the autonomous intelligence stack gave agents the ability to
propose their own goals. It deliberately left every self-generated goal
at SECONDARY priority — behind case-assigned work, never competing for
the agent's attention when real tasks exist. D6 in the Layer 2 spec was
blunt about why: a drive that's running hot — say, accumulated curiosity
from dozens of low-retention memories — could produce a PRIMARY goal that
preempts a case worker's assigned caseload. That's not governance. That's
a runaway feedback loop with production consequences.

Layer 3 narrative identity changed the equation. An agent that has built
a self-narrative — "I am the person who helps in crises," backed by
episodes and themes with measurable salience — has something Layer 2
lacked: *provenance for priority.* The escalation isn't "my curiosity
score is high"; it's "this goal aligns with who I understand myself to
be, and that understanding has been confirmed across multiple independent
narrative synthesis cycles."

## Where the SPI Lives

The original design sketch (D11 in the #142 spec) placed the escalation
policy in engine-api — a `GoalPriorityEscalationPolicy` SPI that
`DefaultGoalFormationService` would call at registration time. The idea
was clean: engine governs priority, blocks provides the implementation.

The dependency graph disagreed. The escalation decision needs
`NarrativeState`, `DriveProfile`, and `DriveGoalProposal` — all blocks
types. Engine-api can't depend on blocks. Putting the SPI there would
mean either generic `Map<String, Object>` attributes (losing type
safety at the governance boundary, which is exactly where you want it
most) or polluting eidos-api with types it shouldn't own.

The fix was straightforward: the policy lives in blocks, where its inputs
live. `GoalProposalOrchestrator` evaluates escalation before converting
proposals to `GoalFormationProposal.ProposedGoal`, setting
`suggestedPriority = PRIMARY` when warranted. The engine already respects
`suggestedPriority` — no engine change needed for the escalation path
itself. For defence-in-depth, `DefaultGoalFormationService` gains a
one-line guard: reject PRIMARY on drive-sourced goals without an
`escalatedBy` attribute. The policy produces the provenance; the engine
verifies it exists.

## Synthesis Cycles as the Governance Clock

The hardest constraint in the governance contract is sustained alignment.
A single tick where a theme's salience is high and its axis weight
matches the goal — that's noise. The narrative could shift next cycle.
Wall-clock time is also wrong: two hours of silence don't mean the
narrative was re-evaluated and the alignment confirmed. They mean nothing
happened.

The right clock is the narrative synthesis cycle. Each time
`NarrativeSynthesiser` runs its LLM and produces a new `NarrativeState`
(gated by the composite trigger — enough new reflections, sufficient
novelty, or quiet period bypass), `synthesisedAt` advances. That's a
genuine re-evaluation. If the theme-goal alignment survives two of those,
the escalation has earned its rank.

Demotion works the same way in reverse. A PRIMARY goal whose escalation
theme fades — the "crisis-helper" theme drops below salience threshold
or its curiosity axis weight goes negative — starts a demotion counter.
Two synthesis cycles of sustained misalignment, and the goal returns to
SECONDARY. Theme-specific demotion: we check the *original* escalation
theme, not whether any theme happens to support the axis. The provenance
is the contract.

## Cross-Axis Composition

The other Layer 2 deferral was cross-axis goals. Per-axis mappers
produce one goal per drive: "explore knowledge gaps" from curiosity,
"reconnect with subject X" from affiliation. But a `DerivedTheme` with
significant positive weights on both curiosity and affiliation suggests
a compound goal — "learn about X by engaging with Y." The theme is the
compositional signal that single-axis mapping can't see.

Detection is heuristic: scan themes for ≥2 positive axis weights above a
configurable threshold. The dominant axis becomes the proposal's `axis()`
(for deduplication and escalation alignment checking), with the full
composition preserved in `crossAxisWeights` attributes. An optional
`CrossAxisGoalEnricher` SPI — backed by `LlmCrossAxisGoalEnricher` via
AgentProvider — replaces the mechanical "Compound goal: connector across
CURIOSITY and AFFILIATION" with something contextually rich. The heuristic
detects; the LLM describes. Same layering pattern as the rest of the
stack.

## The Pipeline

`GoalProposalOrchestrator` grew from a two-phase pipeline (map drives to
goals, rank by intensity) to five phases. The new middle three —
cross-axis composition, escalation evaluation, demotion evaluation — all
gate on narrative availability. When `NarrativeOrchestrator` isn't wired
in, they silently skip. Every drive-sourced goal stays SECONDARY. The
agent works, the goals form, the capacity limits hold — exactly as
Layer 2 designed it.

When narrative IS available, the same orchestrator becomes a governed
escalation pipeline. A `GoalProposalTick.Changes` — renamed from
`Proposed` to reflect the four signal types it now carries — bundles
proposals, abandonments, priority adjustments, and governance attribute
updates into a single atomic output. The scheduler applies all four. The
compositor guarantee holds: no side effects, no direct writes, just
signals.

## What This Opens

The `GoalEscalationPolicy` SPI is a `@FunctionalInterface`.
`NarrativeGoalEscalationPolicy` is the first implementation — narrative
alignment as the escalation evidence. A future
`HumanApprovalEscalationPolicy` could gate PRIMARY on explicit human
confirmation via the oversight system. The SPI doesn't care what the
evidence is. It cares that evidence exists and that the engine can verify
it.

The deeper question is whether sustained narrative alignment is the right
bar for priority escalation, or whether it's just the first bar we can
measure. An agent whose "crisis-helper" theme has persisted across five
synthesis cycles has something an agent with a momentary curiosity spike
doesn't. Whether that something is sufficient to preempt case-assigned
work — that's a deployment decision, not a platform one. The thresholds
are configurable. The governance is structural.
