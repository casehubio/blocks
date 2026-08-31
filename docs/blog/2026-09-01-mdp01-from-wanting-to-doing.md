---
layout: post
title: "From Wanting to Doing"
date: 2026-09-01
author: Mark Proctor
tags: [casehub, blocks, goal-generation, intrinsic-motivation, drive-architecture, compositor-pattern]
issue: 136
entry_type: note
subtype: diary
series: issue-136-layer2-goal-generation
---

# From Wanting to Doing

The drive architecture gave agents a motivational profile — four axes of intensity, modulated by personality and mood, recomputed every tick. But intensity alone doesn't do anything. A curiosity score of 0.7 tells you the agent *wants* to explore. It doesn't tell it *what* to explore, or *how*, or *when* to start.

That's the gap between Layer 1 and Layer 2 in the autonomous intelligence stack. Layer 1 (drives) answers "what does the agent want?" Layer 2 (goal generation) answers "what should the agent do about it?" The theoretical foundation matters here because it shapes the architecture. Getting the theory wrong means building machinery that doesn't map to how motivation actually works.

## The Theory Behind the Axes

Three of the four drive axes come from Self-Determination Theory (Deci & Ryan 2000): autonomy, competence, and relatedness. SDT argues these are innate psychological needs — when satisfied, they promote growth and well-being; when thwarted, they produce diminished motivation and pathology. The fourth axis — curiosity — comes from a separate tradition: intrinsic motivation research going back to Berlyne's 1954 work on exploratory behaviour, refined by Oudeyer and Kaplan (2007) into computational models of knowledge-gap-driven exploration.

Why does this matter for software architecture? Because each axis has a different *signal source* and a different *goal shape*. Curiosity reads knowledge gaps from the memory hygiene system — fragmented, low-retention memories across diverse topics. The corresponding goal is "explore this area." Competence reads engagement trend decline from the strategy learning system. The goal is "improve this skill dimension." Affiliation reads neglected relationships from the user model. Autonomy reads high-confidence intention projections attributed to other actors.

Each axis produces a different kind of goal from a different kind of data. The architecture mirrors this: four `DriveSource` implementations and four parallel `DriveGoalMapper` implementations, each wired to its source orchestrator's cached intermediate data.

## The Compositor That Doesn't Act

`GoalProposalOrchestrator` follows what we call the compositor pattern — `tick()` evaluates mappers, ranks proposals by drive intensity, enforces capacity limits, and caches the result. It does *not* register goals. The scheduler — a consumer-specific component — reads the cached proposals and decides what to do with them. This separation is load-bearing: it enables inspection before registration, prevents duplicate registrations on replay, and aligns with the engine's existing `autoApprove` governance concept.

The same pattern appears in `DriveOrchestrator` one layer down. `tick()` without `record()`. Both are compositors, not accumulators — they derive state from other orchestrators rather than accumulating raw signals. The difference: `DriveOrchestrator` produces a motivational profile (pure derived state, no side effects ever). `GoalProposalOrchestrator` produces proposals that *lead to* side effects via the scheduler. Registration-time validation (deduplication, capacity checks) is the correctness backstop that makes this safe despite the separation.

## Heuristic Default, LLM Opt-in

The goal mappers are heuristic — zero LLM cost. `CuriosityGoalMapper` reads the cached knowledge gap summary and produces "explore-knowledge-gaps" with a templated description. Fast, deterministic, free. But templated goals can feel mechanical.

`DriveGoalFormationStrategy` provides an LLM-backed alternative. When wired into the orchestrator, it's tried first for each axis above threshold. The LLM receives drive-specific context — axis, intensity, trigger provenance, existing goals — and produces richer, more specific proposals. If it fails or returns nothing, the heuristic mapper runs as fallback.

This follows the same tiering pattern we use in the summarisation framework: `HeuristicMessageSummariser` for the fast path, `LlmContentSummariser` for the rich path. The consumer chooses. The default is always the cheap one.

## What This Opens Up

Layer 2 gives agents the ability to propose their own work. The engine's existing goal lifecycle — decomposition, planning, execution, revision, abandonment — handles everything downstream. The agent doesn't need new capabilities to pursue a self-generated goal; it needs a way to *form* one. That's what this layer provides.

Layer 3 is where things get interesting: narrative identity provides the self-model that enables cross-axis goal composition ("learn about X *by engaging with* Y"), governed priority escalation, and the justification framework that prevents runaway drive-sourced goals from preempting assigned work. That's next.
