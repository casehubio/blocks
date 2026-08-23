---
title: "From Wanting to Doing"
author: mdp
date: 2026-08-23
series: issue-136-autonomous-goal-generation
entry_type: note
subtype: diary
projects: [casehubio/blocks, casehubio/engine]
tags: [autonomous-intelligence, goal-formation, compositor-pattern, drive-architecture]
---

# From Wanting to Doing

Two days ago we built the drive system — four axes of intrinsic motivation
that read existing social cognition outputs and produce a motivational
profile. The obvious question: now what? A drive intensity of 0.7 on
curiosity means nothing if it never becomes an action.

The gap turned out to be narrower than I expected. The engine already has a
complete goal lifecycle: formation, decomposition, revision, abandonment,
routing signals, completion marking. Six evaluators and a planner, all
wired up and working. The only problem is that goal formation starts from
`GoalFormationEvaluator.evaluate(workerName, caseInstance, insights)` — it
needs a running case. Autonomous goals fire during idle time when no case
is active.

So the design was less "build a goal system" and more "extract the
case-independent parts of the one that already exists."

## The extraction

`GoalFormationService` pulls validation, deduplication, capacity checking,
and audit logging out of the evaluator into a standalone SPI. The evaluator
keeps its cooldown, memory retrieval, and strategy invocation — it just
delegates registration to the service now. Similarly, `GoalRemovalService`
extracts the shared "remove goals + re-register descriptor + audit"
pattern that both the revision evaluator and our new drive lifecycle need.

One deliberate decision: I didn't refactor `GoalRevisionEvaluator` to use
the removal service. The revision flow does a comprehensive descriptor
replacement — promotions, demotions, description revisions, and removals
in a single atomic register call. Splitting the removal into a separate
service call would cause two registration round-trips with stale state
between them. The service exists for simple removal use cases (drive
relevance re-evaluation). The revision evaluator's flow is fundamentally
different, and forcing delegation would add complexity without benefit.

## The compositor that doesn't register

`GoalProposalOrchestrator` follows the same compositor pattern as
`DriveOrchestrator`: `tick()` evaluates and caches, `currentProposals()`
returns cached state, no side effects. The scheduler reads proposals and
calls `GoalFormationService.propose()` separately.

This matters because goal registration is effectful — it modifies the
agent descriptor and writes an audit log. If the orchestrator registered
goals directly, a double-tick (replay, retry, crash recovery) would
produce duplicate registrations. The compositor guarantee prevents this:
tick produces the same result regardless of how many times it runs. The
effectful step happens exactly once, when the scheduler decides to act.

Drive-sourced goals always use SECONDARY priority. The engine's existing
priority system handles sequencing — SECONDARY goals execute only when no
PRIMARY case-assigned work is available. The priority system IS the
idle-time mechanism. No idle detection needed.

## The bug that isn't obvious

Claude caught something during review that I'd have missed in manual
testing: the orchestrator's mapper loop calls ALL mappers for ALL
above-threshold axes. A curiosity mapper receiving a COMPETENCE intensity
happily reads its own source data and returns a CURIOSITY proposal — but
carrying the COMPETENCE intensity value. The axis in the output is
correct; only the numeric intensity is wrong. And since intensity values
are plausible at any point in [0, 1], the proposals look valid.

The fix is a one-line guard at the top of each mapper:
`if (intensity.axis() != DriveAxis.CURIOSITY) return null`. The nested
loop pattern is correct for CDI discovery where mappers don't declare
their axis — but each mapper must self-filter.

This is exactly the kind of bug that passes all obvious tests. Every
mapper returns proposals for the right axis. Every proposal has a
plausible intensity. The corruption only shows when you compare the
proposal's intensity against the originating drive's actual intensity —
and nothing in the test suite was doing that comparison across axes.

## What Layer 2 doesn't do

Drive-sourced goals are always SECONDARY. There's no pathway for a
drive to preempt case-assigned work — that requires governance that
doesn't exist yet. Cross-axis goal composition ("learn about X by
engaging with Y") requires a holistic self-model. Dynamic priority
based on drive intensity needs narrative justification for elevation.

All of these are Layer 3 territory: narrative identity. The agent
needs a coherent sense of purpose before it can justify taking
initiative over assigned work. Without that justification, runaway
drives could preempt legitimate case-bound goals — an ungoverned
pathway where accumulated curiosity silently starves production work.

The priority system is the safety valve. SECONDARY means "when nothing
else needs doing." That constraint is the right default until
governance exists to relax it.
