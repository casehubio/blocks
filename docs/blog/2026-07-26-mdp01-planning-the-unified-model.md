---
layout: post
title: "Planning the Unified Model"
date: 2026-07-26
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [agentic, execution-model, planning, engine]
---

The unified execution model spec has been sitting consolidated for two weeks. 94KB across 13 parts — the result of merging the reviewed design spec with two research docs and the execution backend architecture. The design was reviewed (10 rounds, 35 issues), but nobody had turned the spec into an execution plan. Time to fix that.

I started by mapping the document landscape. Five docs existed for this topic, three of them fully subsumed by the consolidated spec. The research notes and execution backend architecture doc were still sitting in `docs/` at full size — 25KB each — despite every paragraph already living in the consolidated version. Replaced all three with pointer files. The git history holds the originals.

The more interesting discovery was what had changed in engine since the spec was written. Several prerequisite issues had closed: engine#694 (DagPlan structure), engine#203 (ContextBridge protocol), engine#634 (StrategyResolver architecture), engine#700 (unified orchestration model design). `AgentRef extends ExecutorRef` was already done. `PlanItem` already had its `executor` field as `ExecutorRef`. The spec's prerequisite table was stale before anyone started implementing.

But `DagPlan` still doesn't have `sequentialMerge()`. `PlanItem` is still a flat mutable class — no sealed hierarchy, no Primitive/Compound. Stage is still a distinct type with five containment sets and its own lifecycle. `PlanningStrategyLoopControl` still builds its own strategy map instead of using the `StrategyResolver` that already exists in platform-api. The infrastructure plumbing exists; the structural migration hasn't started.

The original plan in the spec (Phases 0–6) covered only the migration: moving planning types from blocks to engine. I expanded it to cover the full unified model end-to-end. Phase 7 adds agent dispatch wiring — `WorkerAgent`, `ChannelAgent`, and `HumanAgent` currently carry type-level intent but no runtime dispatch path through engine. Without that, the sealed `AgentRef` hierarchy is a promise with no runtime backing. Phase 7 also adds `GoalOrientedDecomposition` (GOAP) and `DispositionAwareRouting` via eidos. Phase 8 adds the Quarkus Flow backend for workflow-shaped patterns — sequence, parallel, loop, conditional all mapping to Serverless Workflow definitions and executing through the existing `FlowWorkerFunctionHandler`.

One embarrassing gap: Claude explored the engine and blocks codebases looking for whether the design spec had been reviewed, searched git history and ADR directories, and concluded it hadn't. It had — 10 rounds, 35 issues, stored at `~/adr/casehub-engine/`. Completely outside the project tree. The review was thorough; we just couldn't find it. Lesson stored for next time.

The plan is 12 phases, sitting in a workspace plan file. Work-slot 38 is set up with engine and blocks worktrees on `issue-60-unified-execution-model`. The slot session will run the design-reviewed spec through implementation, starting at Phase 0. The unreviewed material (Parts 6–13 of the consolidated spec) is background context — the core model decisions were all covered in the original review.

Also updated engine#101 (LLM supervisor mode). Gaps 2 and 3 are done — `Capability` has its `description` field, and blocks' decomposition infrastructure covers goal-to-plan. Gap 1 (LLM-backed PlanningStrategy) is subsumed by the unified execution model: once the HTN planning strategy lands in Phase 5, an LLM variant is a strategy implementation, not a gap.
