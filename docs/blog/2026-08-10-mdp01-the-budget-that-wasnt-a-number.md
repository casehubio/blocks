---
layout: post
title: "The Budget That Wasn't a Number"
date: 2026-08-10
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [agentic, decomposition, planning-constraints, static-decomposition]
---

## The Budget That Wasn't a Number

`PlanningConstraints` has lived in engine-api since the agentic planning work landed. `GoalDecompositionContext` carries them and overrides `constraints()` — so goal decomposition has always had access. But the agentic path through blocks didn't. `AgenticDecompositionContext` inherited the default `constraints()` method, which returns `unconstrained()`. Any decomposition strategy invoked through HTN patterns or static decomposition never saw what the engine was telling it.

The fix is mechanical: add `planningConstraints` as a nullable record component, default null to `unconstrained()` in the compact constructor, override `constraints()`. Thread the value through every place that creates a new context — `HeuristicDecomposition.enrichContext()`, `HybridDecomposition.enrichContext()`, `LlmDecomposition.resolveSubtaskEntry()`. The pattern is identical to what `GoalDecompositionContext` already does.

What's more interesting is what happens once constraints flow through. `StaticDecomposition` is the first consumer to use them structurally — not as prompt text for an LLM, but as a hard gate. Before evaluating a method's guard predicate, it now checks whether the method's `estimatedCost` exceeds the constraint's `costBudgets`, or its `estimatedDuration` exceeds the `timeBudget`. Methods that can't fit within the budget are skipped entirely.

This creates a deliberate layered enforcement model. `LlmDecomposition` already renders constraint text into the planner's prompt — "you have 5000 tokens, plan accordingly." That's advisory. The LLM sees it and generates cost-aware plans, but nothing stops it from ignoring the hint. `StaticDecomposition`'s pruning is structural — a method with `estimatedCost: {tokens: 10000}` against a budget of 5000 tokens is never evaluated, regardless of whether its guard would pass.

The two layers complement each other. LLM planners get soft guidance. Static decomposition gets hard enforcement. And both read from the same `PlanningConstraints` carried on the same `DecompositionContext.constraints()` method. No separate configuration, no divergent state.

`DecompositionMethod` gained `estimatedCost` and `estimatedDuration` as optional fields in engine-api — nullable, backward-compatible. Existing methods without estimates pass through unchecked. The pruning only activates when both the method carries estimates and the context carries constraints with matching dimensions. This means consumers can adopt constraint-based pruning incrementally: add estimates to methods as you learn what they cost, and the system starts filtering automatically.
