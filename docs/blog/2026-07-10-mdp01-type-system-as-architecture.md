---
layout: post
title: "The Type System as Architecture Document"
date: 2026-07-10
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [agentic, task-decomposition, architecture]
---

## Where the Gap Was

I wanted to add LLM-backed goal decomposition to the agentic orchestration framework — an LLM takes a high-level goal and available agents, produces a plan. Straightforward enough. But when I looked at where the output would go, the type system told me the design was wrong.

`PrimitiveTask` carried an `AgentRef`, a `Predicate<T>` precondition, and a `Consumer<T>` effect. An LLM can name agents and describe tasks in natural language. It cannot produce Java lambdas. Cramming LLM output into `PrimitiveTask` with always-true predicates and no-op consumers would compile — and lie about what the task actually is.

The deeper problem: `PrimitiveTask` had no description field. Its identity was implicit from the agent it pointed at. Every task decomposition framework in the research literature gives tasks a human-readable description. Ours didn't.

## What the Research Showed

I surveyed eight frameworks — Graph Harness, RSTD, ChatHTN, LLMCompiler, AgentOrchestra, GAP, LLM-Generated HTN Heuristics, and DSPy. The capability matrix was revealing: CaseHub is strong where others are weak (trust routing, oversight gates, CBR evidence, typed agent identity via eidos) and weak where others are strong (no DAG plan structure, no task descriptions, no parallel execution, no output validation, no multi-level recovery).

The gap is structural. We have the right SPIs — five strategy interfaces that compose cleanly — but the plan representation beneath them is too thin. Linear task lists with no identity, no dependencies, no output contracts.

## The LeafTask Decision

Rather than just adding a `PlannedTask` variant, I restructured the sealed hierarchy:

```
TaskNode<T>
├── LeafTask<T>          ← shared agent() + description()
│   ├── PrimitiveTask    ← HTN leaf
│   └── PlannedTask      ← LLM leaf
└── CompoundTask         ← decomposable goal
```

`LeafTask` is the shared contract for anything executable. Switch expressions can match at either granularity — `LeafTask` for "anything I can dispatch," or the specific variant when the planning paradigm matters. Future work (output contracts, DAG node metadata) attaches to `LeafTask`, not to each variant independently.

The design review caught a weight ordering error in the CBR scoring — I had GATE_EXPIRED penalising more harshly than GATE_REJECTED. The reviewer's argument was clean: an expired gate means nobody reviewed it (the oversight process failed, not the worker), while a rejection means a human explicitly judged the action inappropriate. The harsher penalty belongs on the explicit rejection. Obvious in hindsight.

## The Architectural Question

The conversation that mattered most wasn't about either issue. It was about why the agentic patterns in blocks sit "above" the blackboard in engine, rather than alongside it as peers.

Blackboard, Serverless Workflow, and the agentic orchestration patterns all answer the same question: how do I coordinate work across multiple agents? They all produce units of work assigned to executors. They all track lifecycle state. But they use parallel type hierarchies that evolved independently — `PlanItem` in engine, `TaskNode` in blocks, `PlannedAction` in worker-api.

The layering is historical, not architectural. A unified model — shared task types, peer coordination strategies, recursive nesting with type-safe context propagation — is the right long-term direction. That's engine#700, and it's XL/High complexity. But the `PlannedTask` we built today is designed to bridge cleanly into it: `description` + `agent` are exactly the fields a shared `Task` interface would carry.

Ten issues across two repos. The immediate work closes two of them. The rest maps the path from where the platform is to where the research says it should be.
