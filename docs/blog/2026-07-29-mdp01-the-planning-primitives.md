---
layout: post
title: "The Planning Primitives CaseHub Was Missing"
date: 2026-07-29
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [agentic, decomposition, routing, htn]
---

CaseHub's agentic planning framework had the architecture — sealed task nodes, decomposition strategies, routing signals — but the ergonomics lagged. Defining a three-step plan required six-argument constructors with auto-generated IDs and timestamps that nobody cared about. Static decomposition couldn't reason about what upstream tasks would produce. When the LLM fallback kicked in, it had no idea what the static planner had already tried. Six issues, each closing a gap between "the framework supports this" and "a developer can actually use this."

## The task tree DSL

The most visible change. Before:

```java
var validate = new PrimitiveTask<>(UUID.randomUUID().toString(),
    Instant.now(), "validate", agent, null, s -> s.setType("DIGITAL"));
var sendLink = new PrimitiveTask<>(UUID.randomUUID().toString(),
    Instant.now(), null, linkAgent, null, null);
var review = new PrimitiveTask<>(UUID.randomUUID().toString(),
    Instant.now(), null, reviewAgent, null, null);
var fulfill = new CompoundTask<>("fulfill", List.of(
    new DecompositionMethod<>(s -> "DIGITAL".equals(s.type()), /* strategy */),
    new DecompositionMethod<>(s -> true, /* strategy */)));
```

After:

```java
compound("process",
    primitive(agent, s -> s.setType("DIGITAL")),
    compound("fulfill",
        decompose(s -> "DIGITAL".equals(s.type()), primitive(linkAgent)),
        decompose(primitive(reviewAgent))),
    primitive(confirmAgent))
```

The tree structure reads as what it means — a process with a conditional branch inside it. `compound(name, children...)` accepts a mix of leaf and compound nodes; internally it wraps them in a `SequenceStrategy` that recursively decomposes via `StaticDecomposition`. IDs and timestamps are auto-generated. The constructor ceremony disappears.

## Forward reasoning makes the guards work

Without forward reasoning, `StaticDecomposition` evaluates the `"DIGITAL".equals(s.type())` guard against the *original* state — before the validate step has run. The guard fails, and the planner falls through to manual review even though validate would have set the type to DIGITAL.

`ForwardReasoningDecomposition` is a drop-in replacement for `StaticDecomposition` on pure-symbolic trees. It copies the state, then walks children depth-first: when it hits a `PrimitiveTask` with an effect, it applies the effect to the projected state before evaluating the next guard. The validate step's effect propagates forward, the DIGITAL guard matches, and the planner selects `sendLink`. This is what SHOP-style HTN planners do — CaseHub's `LlmDecomposition` reasons about future state naturally, but the symbolic planner was blind to it.

## Output contracts

In multi-step LLM pipelines, the most common failure mode is cascading reasoning errors — task A produces garbage, task B reasons on that garbage, and the pipeline produces confident wrong output. The RSTD paper showed that validating outputs between tasks and retrying at task granularity (not pipeline restart) cuts retry costs by half. `OutputContract` is a `@FunctionalInterface` with composable factories:

```java
var contract = OutputContract.type(String.class).and(OutputContract.nonNull());
primitive("analyse", agent, contract)
```

The execution driver validates the output against the contract before passing it downstream. Failed validation triggers retry of that specific task. Both `PrimitiveTask` and `PlannedTask` carry an optional contract — existing code is unaffected (backward-compatible constructors).

## Predecessor-aware routing

`PlanCompositionAnalyser` already scores candidates by their overall success in multi-step plans. `PredecessorAnalyser` goes deeper: it scores candidates based on *who preceded them*. For a three-step plan A→B→C, if worker X consistently succeeds at step C when worker Y did step B, that pairing signal is captured. This matters in domains where agent sequencing has implicit dependencies — a forensic analyst performs differently after a triage bot than after a monitoring agent, even when the task description is identical.

## LLM fallback enrichment

When `HybridDecomposition` falls back from static to LLM planning, the LLM should know what was already tried. `NoMethodMatchedException` now carries the method count, and the LLM prompt includes: "3 static method(s) evaluated, none matched for 'respond-to-incident'." Whether this helps the LLM avoid duplicating the static planner's approach is an empirical question — but the context was being discarded for no reason.

## Conversation projection hook

The smallest change and the most overdue. Drafthouse's `DebateChannelProjection` has been double-parsing metadata headers since the conversation extraction — intercepting entry types outside the base class's try-catch, then delegating to `super.apply()` which parses them again. A protected hook returning `Set<String>` of types to skip. One check in `doApply()`, the double-parse is gone.

The forward reasoning and output contracts together close two of the three gaps the RSTD paper identifies: cascading reasoning errors (effect propagation during planning) and unvalidated intermediate outputs (per-task contracts). The third — selective retry driven by the contracts — needs the execution driver to consume them, which is engine work.
