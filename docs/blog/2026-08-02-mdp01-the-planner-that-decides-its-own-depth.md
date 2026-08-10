---
layout: post
title: "The Planner That Decides Its Own Depth"
date: 2026-08-02
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [agentic, decomposition, htn, llm-planning]
---

Flat plans break on complex goals. Ask an LLM to decompose "investigate a fraud case" into agent assignments and you get a sequence of fifteen steps, half of which are at the wrong level of abstraction. Step 4 says "analyse the financial records" — but that's not a single agent action, it's an entire sub-plan. The LLM knows this. It just has no way to say so.

## The depth parameter

`LlmDecomposition` now takes a `maxDepth` parameter. At `maxDepth = 1` (the default), nothing changes — the LLM produces flat agent assignments, same as before. At `maxDepth = 2`, the response format expands. The LLM can return two kinds of entries:

```json
[
  {"agent": "analyst", "task": "review the data", "rationale": "domain expert"},
  {"subtask": "prepare-report", "description": "compile findings into a structured report"}
]
```

An `agent` entry becomes a `PlannedTask` — an executable leaf. A `subtask` entry becomes a `CompoundTask` and gets its own `LlmDecomposition` call at `depth + 1`. At the last level, only agent assignments are accepted. The full tree materialises as a `DagPlan<LeafTask<T>>` before anything executes.

This is the key constraint. CaseHub treats plans as data — inspectable, auditable, gateable. The recursive tree is fully resolved into a DAG of leaf tasks before the first agent runs. No mid-execution re-planning, no lazy expansion. The oversight gates, trust routing, and compliance audit trail all work because the plan is a concrete data structure, not an unfolding process.

## What the LLM actually sees

The interesting design question was what context to give the recursive calls. A subtask decomposed in isolation produces orphaned steps — the LLM doesn't know what the parent goal was, what its siblings are doing, or how its work fits into the larger plan. The recursive prompt includes all three:

```
Parent goal: investigate-fraud
Sibling tasks: review data, prepare-report
Goal: prepare-report
Description: compile findings into a structured report
```

The system prompt also switches between two variants. At depth < maxDepth - 1, the LLM is told it can create subtasks. At the last level, the prompt drops the subtask format entirely — the LLM can only assign to agents. This prevents unbounded recursion at the prompt level, not just the code level.

## The cost model

Each decomposition level multiplies LLM calls by the branching factor. With a branching factor of 5 and `maxDepth = 2`, that's 6 calls (1 + 5). At `maxDepth = 3`, it's 31 (1 + 5 + 25). At `maxDepth = 4`, 156. Growth is O(N^d) — practical for 2, cautious at 3, impractical beyond that for most use cases. The sweet spot is `maxDepth = 2`, where the LLM gets one shot at saying "this part is complex" and the sub-planner resolves it to concrete actions.

## Where this fits

CaseHub's decomposition package now covers the full spectrum. `StaticDecomposition` handles known task structures with guard predicates — fast, deterministic, no LLM cost. `ForwardReasoningDecomposition` applies effects to projected state during planning (SHOP-style), so downstream guards see what upstream tasks will produce. `GoalOrientedDecomposition` does backward-chaining GOAP from desired capabilities. `HybridDecomposition` tries static methods first and falls back to the LLM when no method matches — the ChatHTN pattern.

Recursive `LlmDecomposition` fills the remaining gap: goals so novel that no static method exists, complex enough that flat planning produces the wrong granularity. The LLM decides what needs further breakdown. The depth ceiling keeps costs bounded. The result is still a `DagPlan` — same type, same execution drivers, same accountability listeners.

The research literature has been converging on hierarchical decomposition as the production pattern for a while now — Graph Harness, AgentOrchestra, ChatHTN all use it. The difference is that most frameworks expand the tree lazily during execution. CaseHub materialises it eagerly. The plan is data before the first agent runs, which means the oversight gates and compliance audit trail work without modification. Whether that trade-off holds at higher depths is an open question — at `maxDepth = 3` with a branching factor of 5, you're materialising 31 sub-plans before anything executes. For fraud investigation or clinical triage, that's probably fine. For real-time event response, it might not be.
