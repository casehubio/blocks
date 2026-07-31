# Recursive LLM Decomposition — Design Spec

**Issue:** casehubio/blocks#54
**Date:** 2026-07-30
**Branch:** issue-54-recursive-llm-decomposition

---

## Problem

`LlmDecomposition` produces only flat plans — every LLM response entry becomes
a `PlannedTask` (leaf). For complex goals, the LLM should be able to indicate
"this part needs further breakdown" by returning subtask entries that become
`CompoundTask` nodes, recursively decomposed until a `maxDepth` ceiling. The
recursive tree is fully materialized as a `DagPlan<LeafTask<T>>` before
returning — preserving CaseHub's plan-as-data advantage.

## Design Decision

Recursion lives inside `LlmDecomposition.decompose()`, not in an external
wrapper. The recursion is inherently LLM-specific: the system prompt must be
depth-aware (whether to allow subtask entries), and the LLM decides what needs
further decomposition. Separating recursion from the prompt logic would require
the wrapper to reach into the LLM strategy's internals, breaking encapsulation.

## Changes

### 1. `LlmDecomposition` — `maxDepth` parameter

Add a `maxDepth` field (default 1 = current flat behavior). Constructor
overloads:

```java
public LlmDecomposition(AgentProvider agentProvider,
                         Function<T, String> stateRenderer,
                         int maxDepth)

public LlmDecomposition(AgentProvider agentProvider, int maxDepth)
```

Validation: `maxDepth >= 1`, otherwise `IllegalArgumentException`.

Existing constructors unchanged — they default to `maxDepth = 1`.

### 2. Depth-aware system prompt

Two prompt variants:

**Flat-only (current behavior, used when `depth >= maxDepth - 1`):**

```
You are a task planner. Given a goal, current state, and available agents,
decompose the goal into a sequence of agent tasks.

Respond with JSON only:
[{"agent": "<name>", "task": "<description>", "rationale": "<why>"}]
```

**Recursive (used when `depth < maxDepth - 1`):**

```
You are a task planner. Given a goal, current state, and available agents,
decompose the goal into steps. Each step is either:
- A concrete task assigned to an agent
- A subtask that needs further decomposition

Respond with JSON only. Each entry is one of:
  {"agent": "<name>", "task": "<description>", "rationale": "<why>"}
  {"subtask": "<name>", "description": "<what needs to be done>"}

Use subtasks for complex parts that need multi-step planning.
Use agent assignments for concrete actions an agent can execute directly.
```

**User prompt enrichment for recursive calls:**

When decomposing a subtask (depth > 0), `buildUserPrompt` includes
hierarchical context from `AgenticDecompositionContext`:

```
Parent goal: <parentGoal>
Sibling tasks: <sibling1>, <sibling2>, ...
Goal: <subtask name>
Description: <subtask description>
Current state: ...
Available agents: ...
```

This ensures the recursive LLM call has the context needed to produce a
coherent decomposition that complements its siblings and serves the
parent goal.

### 3. LLM response parsing

The existing `parseResponse` method is refactored to return
`List<TaskNode<T>>` (up from `List<TaskNode.LeafTask<T>>`). The
`decompose` method then handles resolution and merging in three phases:

**Phase 1 — Parse entries:** Each JSON array element is classified:
- Has `"agent"` key → agent assignment → `PlannedTask` (leaf)
- Has `"subtask"` key (no `"agent"`) → subtask entry →
  `CompoundTask(name, List.of())` with the `description` field captured
  separately
- Has both → agent assignment (agent takes priority)
- Has neither → skipped with warning

Phase 1 returns `List<TaskNode<T>>` — a heterogeneous list of leaf tasks
and compound tasks — paired with a `Map<String, String>` of subtask name
to description.

**Phase 2 — Resolve entries:** For each entry from Phase 1:
- If `LeafTask` → singleton `DagPlan`
- If `CompoundTask` and `depth + 1 >= maxDepth` → warning logged
  ("Subtask '\<name\>' at depth \<d\> exceeds maxDepth \<max\> — skipping"),
  entry skipped
- If `CompoundTask` and `depth + 1 < maxDepth`:
  - Create new `AgenticDecompositionContext` with `depth + 1`, same
    `state`, same `agents`, `subtaskDescription` set to the captured
    description, `parentGoal` set to the current compound task's name,
    `siblingNames` set to the names of all Phase 1 entries
  - Call `this.decompose(compoundTask, newContext)` →
    `Uni<DagPlan<LeafTask<T>>>`

**Phase 3 — Merge:** Collect all sub-plans and merge with
`DagPlan.sequentialMerge(subPlans)`.

### 4. Recursion mechanics

The recursion is sequential and blocking, consistent with the existing
code style (which uses `.await().indefinitely()` inside
`Uni.createFrom().item()`). Each subtask is decomposed in order. The
result is a flat `DagPlan<LeafTask<T>>` with sequential dependencies
between the sub-plans.

Depth tracking uses the existing `AgenticDecompositionContext.depth()`
field.

**Cost model:** Each decomposition level multiplies the LLM call count
by the branching factor (subtasks per level). With branching factor N:

- `maxDepth = 1`: 1 LLM call (flat, current behavior)
- `maxDepth = 2`: 1 + N₁ calls (one per subtask)
- `maxDepth = 3`: 1 + N₁ + N₁×N₂ calls

Growth is O(N^d) where d = maxDepth and N is the average branching
factor. For most use cases, `maxDepth = 2` provides sufficient
decomposition depth. Values above 3 should be used with caution — a
branching factor of 5 at `maxDepth = 4` yields 156 LLM calls and 625
leaf tasks.

### 5. Error handling

All error and warning messages in recursive contexts include the compound
task name and current depth for debuggability.

| Scenario | Behavior |
|----------|----------|
| Subtask entry at max depth | Warning: "Subtask '\<name\>' at depth \<d\> exceeds maxDepth \<max\> — skipping" |
| Empty recursive decomposition | `IllegalStateException`: "LLM returned empty plan for '\<name\>' at depth \<d\>" |
| LLM failure during recursion | Exception propagates up (original exception preserved) |
| Unknown agent in recursive call | Warning: "LLM named unknown agent '\<agent\>' for '\<taskName\>' at depth \<d\> — skipping step" |
| All entries skipped | `IllegalStateException`: "LLM returned empty plan for '\<name\>' at depth \<d\>" |

### 6. `HybridDecomposition` changes

Add convenience constructors with `maxDepth`:

```java
public HybridDecomposition(AgentProvider agentProvider, int maxDepth)
public HybridDecomposition(AgentProvider agentProvider,
                           Function<T, String> stateRenderer, int maxDepth)
```

These pass `maxDepth` through to `LlmDecomposition`. The full-control
constructor `(DecompositionStrategy, DecompositionStrategy)` is unchanged.

### 7. `Decomposition` DSL changes

Add `maxDepth` overloads:

```java
public static <T> HybridDecomposition<T> hybrid(AgentProvider provider, int maxDepth)
public static <T> HybridDecomposition<T> hybrid(AgentProvider provider,
                                                 Function<T, String> renderer,
                                                 int maxDepth)
```

### 8. `AgenticDecompositionContext` constructor hierarchy

The record gains three nullable fields, expanding the canonical
constructor from 4 to 7 parameters:

```java
// New canonical (7 params)
public record AgenticDecompositionContext<T>(
    T state, List<RoutingCandidate> agents, int depth,
    @Nullable String staticFailureHint,
    @Nullable String subtaskDescription,
    @Nullable String parentGoal,
    @Nullable List<String> siblingNames)
    implements DecompositionContext<T> { ... }
```

Convenience constructors preserve all existing call sites:

```java
// Preserves old canonical — used by HybridDecomposition.enrichContext()
// and LlmDecompositionTest
public AgenticDecompositionContext(T state, List<RoutingCandidate> agents,
                                   int depth, @Nullable String staticFailureHint) {
    this(state, agents, depth, staticFailureHint, null, null, null);
}

// Preserves existing 3-arg — used by ForwardReasoningDecomposition,
// HtnBuilder, and all other test files
public AgenticDecompositionContext(T state, List<RoutingCandidate> agents,
                                   int depth) {
    this(state, agents, depth, null, null, null, null);
}
```

`HybridDecomposition.enrichContext()` must propagate the new fields
when copying a context:

```java
return new AgenticDecompositionContext<>(ac.state(), ac.agents(), ac.depth(),
    hint, ac.subtaskDescription(), ac.parentGoal(), ac.siblingNames());
```

This uses the 7-arg canonical constructor directly, preserving all
context fields while overriding only `staticFailureHint`.

`ForwardReasoningDecomposition.expandMethod()`, `HtnBuilder.flatten()`,
and all test files continue to compile via the 3-arg convenience
constructor — no changes needed.

## SPI contract

**Unchanged.** `DecompositionStrategy.decompose()` still returns
`Uni<DagPlan<TaskNode.LeafTask<T>>>`. The recursion is entirely internal
to `LlmDecomposition`. No engine-api changes required. No consumer changes
required.

## Backward compatibility

`maxDepth = 1` (default) preserves exact current behavior — the flat-only
system prompt is used, subtask entries in responses are skipped.

`AgenticDecompositionContext` convenience constructors (3-arg and 4-arg)
preserve source compatibility for all existing call sites —
`ForwardReasoningDecomposition`, `HtnBuilder`, and 8+ test files compile
without modification. Only `HybridDecomposition.enrichContext()` changes
(to propagate the new fields when copying a context).

All existing tests pass without modification.

## Testing strategy

### Existing tests (unchanged)

All current `LlmDecompositionTest` and `HybridDecompositionTest` tests
pass as-is — they use default constructors (`maxDepth = 1`).

### New tests

**Recursive decomposition (maxDepth = 2):**
- Mixed response (agent assignments + subtasks) → correct merged DAG
- All-subtask response → each recursively decomposed, merged sequentially
- Recursive call returns multi-step plan → sub-DAG correctly wired
- Task ordering preserved across recursive boundaries

**Depth enforcement:**
- `maxDepth = 1`, subtask in response → skipped with warning
- `maxDepth = 2`, recursive call at depth=1 → flat-only prompt used
- `maxDepth = 3` → three-level recursion, correct flat DAG

**Prompt construction:**
- `depth < maxDepth - 1` → prompt includes subtask format
- `depth == maxDepth - 1` → flat-only prompt (current format)
- Subtask name used as "Goal:" in recursive prompt
- Subtask description included in recursive prompt when present
- Parent goal and sibling names included in recursive prompt

**Error handling:**
- Subtask at max depth → skipped with warning including name and depth, remaining entries processed
- Empty recursive decomposition → `IllegalStateException` with task name and depth
- Unknown agent warning includes task name and depth
- LLM failure during recursion → propagates

**Constructor validation:**
- `maxDepth < 1` → `IllegalArgumentException`

**HybridDecomposition:**
- `maxDepth` constructors pass through to LLM fallback correctly

## Files changed

| File | Change |
|------|--------|
| `LlmDecomposition.java` | `maxDepth` field, depth-aware prompt, recursive parse/resolve/merge |
| `AgenticDecompositionContext.java` | New fields: `subtaskDescription`, `parentGoal`, `siblingNames`; 4-arg and 3-arg convenience constructors preserving existing call sites |
| `HybridDecomposition.java` | `maxDepth` convenience constructors; `enrichContext()` propagates new context fields |
| `Decomposition.java` | `maxDepth` overloads on `hybrid()` |
| `LlmDecompositionTest.java` | New test groups for recursion, depth, prompts, errors, context enrichment |
| `HybridDecompositionTest.java` | Tests for `maxDepth` constructors |
