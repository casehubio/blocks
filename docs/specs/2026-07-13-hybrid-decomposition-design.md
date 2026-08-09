# Hybrid Decomposition Strategy — Design Spec

**Issue:** casehubio/blocks#45
**Parent:** #44 (agentic planning architecture epic)
**Date:** 2026-07-13

---

## Summary

Implement `HybridDecomposition<T>`, a `DecompositionStrategy<T>` that composes
`StaticDecomposition<T>` and `LlmDecomposition<T>`. Try static first — fast,
deterministic, sound. Fall back to LLM when no method guard matches — flexible,
handles novel goals. LLM fallback plans inherit `LlmDecomposition`'s existing
validation (agent resolution, non-empty plan check). Full symbolic validation
of LLM output (precondition/effect checking per ChatHTN) requires #56.

Inspired by [ChatHTN](https://arxiv.org/html/2505.11814), which interleaves
symbolic HTN planning with LLM decomposition. CaseHub's variant differs from both
ChatHTN and langchain4j's HTN (langchain4j/langchain4j#5584) by producing an
immutable `ExecutionPlan<T>` DAG upfront rather than stepping through the tree
imperatively — preserving plan-as-data for inspection, audit, and parallel execution.

## Architectural context

CaseHub's decomposition infrastructure:

```
DecompositionStrategy<T>            ← SPI interface
├── StaticDecomposition<T>          ← guard-based method selection
├── LlmDecomposition<T>            ← LLM-driven via AgentProvider
├── IdentityDecomposition<T>       ← leaf passthrough
└── HybridDecomposition<T>         ← NEW: static-first, LLM fallback
```

The hybrid sits at the same level as the existing strategies. It delegates to
`StaticDecomposition` and `LlmDecomposition` — it does not subclass or modify them.

## New types

### `NoMethodMatchedException`

Package: `io.casehub.blocks.agentic.decomposition`

```java
public class NoMethodMatchedException extends IllegalStateException {
    private final String taskName;

    public NoMethodMatchedException(String taskName) {
        super("No decomposition method guard matched for task '" + taskName + "'");
        this.taskName = taskName;
    }

    public String taskName() { return taskName; }
}
```

Replaces the raw `IllegalStateException` currently thrown by `StaticDecomposition`
when no guard matches. Gives `HybridDecomposition` a type-safe predicate for
fallback — no message string matching.

**Breaking change to `StaticDecomposition`:** the exception type changes from
`IllegalStateException` to `NoMethodMatchedException` (which extends it). Callers
catching `IllegalStateException` are unaffected. Pre-release — no backward
compatibility concern.

### `HybridDecomposition<T>`

Package: `io.casehub.blocks.agentic.decomposition`

```java
public class HybridDecomposition<T> implements DecompositionStrategy<T> {

    private final DecompositionStrategy<T> primaryStrategy;
    private final DecompositionStrategy<T> fallbackStrategy;

    // Convenience — creates StaticDecomposition + LlmDecomposition
    public HybridDecomposition(AgentProvider agentProvider) { ... }

    // With custom state renderer for LLM prompt
    public HybridDecomposition(AgentProvider agentProvider,
                                Function<T, String> stateRenderer) { ... }

    // Full control — any two strategies
    public HybridDecomposition(DecompositionStrategy<T> primaryStrategy,
                                DecompositionStrategy<T> fallbackStrategy) { ... }

    @Override
    public Uni<ExecutionPlan<T>> decompose(TaskNode<T> compound,
                                            DecompositionContext<T> context) { ... }
}
```

#### `decompose()` behaviour

1. Delegate to `primaryStrategy.decompose(compound, context)`
2. If primary succeeds → return its plan (log at DEBUG)
3. If primary fails with `NoMethodMatchedException` → log at INFO
4. If falling back with empty agent list → log WARNING:
   `"Fallback for task '{name}' has no agents — call .agents() on the builder"`
5. Delegate to `fallbackStrategy.decompose(compound, context)`
6. Any other exception from primary → propagate unchanged
7. If fallback succeeds → return its plan
8. If fallback fails → propagate (both strategies failed, nothing to recover)

#### Logging

All via `System.Logger` (consistent with `LlmDecomposition`):

| Event | Level | Content |
|-------|-------|---------|
| Primary succeeded | DEBUG | Task name |
| Falling back | INFO | Task name, that no guard matched |
| Fallback with empty agents | WARNING | Task name, guidance to call `.agents()` |
| Fallback plan produced | DEBUG | Task count, agent names |

## Changes to existing types

### `StaticDecomposition<T>`

One change: throw `NoMethodMatchedException` instead of raw `IllegalStateException`.

Before:
```java
return Uni.createFrom().failure(
    new IllegalStateException("No decomposition method guard matched"));
```

After:
```java
return Uni.createFrom().failure(
    new NoMethodMatchedException(ct.name()));
```

No other changes to `StaticDecomposition`.

### `CompoundTask`

Add null validation for `name` in the compact constructor, consistent with
`PrimitiveTask` and `PlannedTask`:

```java
public CompoundTask {
    Objects.requireNonNull(name, "name");
    methods = List.copyOf(methods);
}
```

### `HtnBuilder`

Two changes:

1. **`flatten()` delegates to `this.decomposition`** instead of reimplementing
   guard matching inline. See §Integration for details.

2. **`agents()` overridden as public.** Currently `protected` on
   `AbstractPatternBuilder`. Required when using `HybridDecomposition` — the
   LLM fallback needs to know available agents for planning.

### `Decomposition`

Add `hybrid()` factory methods for API consistency with `none()` and `staticTree()`:

```java
public static <T> HybridDecomposition<T> hybrid(AgentProvider agentProvider) {
    return new HybridDecomposition<>(agentProvider);
}

public static <T> HybridDecomposition<T> hybrid(AgentProvider agentProvider,
                                                  Function<T, String> stateRenderer) {
    return new HybridDecomposition<>(agentProvider, stateRenderer);
}
```

## Integration

### `HtnBuilder`

`HtnBuilder.flatten()` currently reimplements guard matching inline rather than
delegating to `this.decomposition`. The `DecompositionStrategy` set via
`.decompose()` is stored but never invoked during task tree flattening — making
any non-default strategy (including `HybridDecomposition`) dead code.

**Required change:** Replace the inline guard matching in `flatten()` with
delegation to `this.decomposition.decompose()`:

```java
case TaskNode.CompoundTask<T> compound -> {
    var agents = candidateSupplier != null
            ? candidateSupplier.get() : List.<RoutingCandidate>of();
    var ctx = new DecompositionContext<>(state, agents, 0);
    yield decomposition.decompose(compound, ctx);
}
```

This change:
1. Eliminates duplicate guard-matching logic between `flatten()` and `StaticDecomposition`
2. Enables any `DecompositionStrategy` to work with `HtnBuilder`
3. Resolves the empty agent list in `DecompositionContext` — agents now come from
   the builder's candidate supplier
4. Eliminates the duplicate `IllegalStateException` throw (the strategy handles errors)

`HtnBuilder` must also expose `agents()` as public (currently `protected` on
`AbstractPatternBuilder`) so users can provide available agents for the LLM prompt:

```java
Patterns.<String>htn()
    .decompose(new HybridDecomposition<>(agentProvider))
    .agents(dbFailoverAgent, verifyAgent, notifyAgent)
    .rootTask(tree)
    .execute(state);
```

When using `StaticDecomposition` (the default), `.agents()` is not required — static
decomposition ignores the agent list in `DecompositionContext`. When using
`HybridDecomposition`, `.agents()` is required so the LLM fallback knows which
agents are available for planning.

### Pattern DSL

No changes to `Patterns` entry point. Users construct `HybridDecomposition`
directly and pass it to `.decompose()`.

## Example: incident response workflow

An incident response system where routine incidents follow known playbooks (static
methods) but novel incidents need LLM-driven planning.

**Domain state:**
```java
record IncidentState(String type, String severity, String description) {}
```

**Task tree — three static playbooks, no catch-all:**
```java
var root = new CompoundTask<>("respond-to-incident", List.of(
    new DecompositionMethod<>(state -> "DATABASE_OUTAGE".equals(state.type()), (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.sequence(List.of(
            Decomposition.primitive("failover-db", dbFailoverAgent),
            Decomposition.primitive("verify-connectivity", verifyAgent),
            Decomposition.primitive("notify-stakeholders", notifyAgent)))), null),
    new DecompositionMethod<>(state -> "SECURITY_BREACH".equals(state.type()), (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.sequence(List.of(
            Decomposition.primitive("isolate-systems", isolateAgent),
            Decomposition.primitive("forensic-analysis", forensicsAgent),
            Decomposition.primitive("notify-stakeholders", notifyAgent)))), null),
    new DecompositionMethod<>(state -> "NETWORK_FAILURE".equals(state.type()), (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.sequence(List.of(
            Decomposition.primitive("diagnose-network", diagnoseAgent),
            Decomposition.primitive("reroute-traffic", rerouteAgent),
            Decomposition.primitive("notify-stakeholders", notifyAgent)))), null)
));
```

**With `StaticDecomposition`:** a novel incident type ("PERFORMANCE_DEGRADATION")
throws `NoMethodMatchedException`.

**With `HybridDecomposition`:** the static path fails, the LLM receives the
incident state + available agents, and constructs a plan appropriate to the novel
incident type.

## Test plan

### Unit tests — `HybridDecompositionTest`

| Test | What it verifies |
|------|-----------------|
| `staticSucceeds_llmNotInvoked` | Guard matches → static plan returned, LLM never called |
| `staticFails_llmProducesPlan` | No guard matches → LLM fallback produces valid plan |
| `staticFails_llmAlsoFails_errorPropagates` | Both strategies fail → exception propagated |
| `leafTask_passedThrough` | LeafTask input → returned unchanged without decomposition |
| `multipleGuards_firstMatchWins` | Second guard matches → first skipped, LLM not invoked |
| `nonGuardException_propagatesWithoutFallback` | NPE in guard → propagates, LLM not invoked |
| `convenienceConstructor_createsWorkingInstance` | Single-arg constructor works end-to-end |
| `stateRendererConstructor_passesRendererToLlm` | Custom renderer reaches LLM prompt |
| `fullControlConstructor_usesBothStrategies` | Pre-built strategies used as-is |
| `fallbackLogsAtInfo` | INFO log emitted on fallback (verify via log capture) |
| `fallbackWithEmptyAgents_logsWarning` | Primary fails, agent list is empty → WARNING log emitted with guidance message, fallback still invoked |

### `NoMethodMatchedExceptionTest`

| Test | What it verifies |
|------|-----------------|
| `carriesTaskName` | `taskName()` returns the compound task name |
| `extendsIllegalStateException` | `instanceof IllegalStateException` is true |
| `messageIncludesTaskName` | Exception message contains the task name |

### `HtnBuilderTest` — additional tests

| Test | What it verifies |
|------|-----------------|
| `flattenDelegatesToDecompositionStrategy` | `flatten()` delegates to the configured decomposition strategy, not inline guard matching |
| `hybridDecomposition_fallsBackToLlm` | `HtnBuilder` with `HybridDecomposition` falls back to LLM when no guard matches |
| `agents_passedToDecompositionContext` | Builder-provided agents appear in `DecompositionContext` passed to strategy |

### `StaticDecompositionTest` — additional test

| Test | What it verifies |
|------|-----------------|
| `throwsNoMethodMatchedException` | Existing "no match" test updated to assert `NoMethodMatchedException` |

### Example test — `IncidentResponseHybridTest`

Full integration-style test using the incident response domain:
- Database outage → static playbook fires
- Security breach → static playbook fires
- Novel incident → LLM fallback produces plan
- Verifies agent ordering, correct agents selected, logging

## Files

| Path | Change |
|------|--------|
| `src/main/java/.../decomposition/NoMethodMatchedException.java` | New |
| `src/main/java/.../decomposition/HybridDecomposition.java` | New |
| `src/main/java/.../decomposition/StaticDecomposition.java` | Throw `NoMethodMatchedException` |
| `src/main/java/.../decomposition/TaskNode.java` | Add null check on `CompoundTask.name` |
| `src/main/java/.../decomposition/Decomposition.java` | Add `hybrid()` factory methods |
| `src/main/java/.../pattern/HtnBuilder.java` | Delegate `flatten()` to `this.decomposition`, expose `agents()` |
| `src/test/java/.../decomposition/HybridDecompositionTest.java` | New |
| `src/test/java/.../decomposition/NoMethodMatchedExceptionTest.java` | New |
| `src/test/java/.../decomposition/StaticDecompositionTest.java` | Update existing test |
| `src/test/java/.../pattern/HtnBuilderTest.java` | Add decomposition delegation tests |
| `src/test/java/.../decomposition/examples/incident/IncidentResponseHybridTest.java` | New — example |

## Out of scope

- Recursive LLM decomposition → #54
- Forward reasoning with effects during planning → #56
- Task tree construction DSL → #57
- Passing failed static method context to LLM prompt → #58. Safe to defer:
  the LLM currently receives goal name, state, and available agents — sufficient
  for novel decomposition. Richer context (method descriptions, guard failure
  reasons) requires `String description` on `DecompositionMethod` and/or
  serializable guards. The initial hybrid should be evaluated empirically before
  investing in prompt enrichment — the LLM may produce equivalent plans with or
  without static method context.

## Related issues

- #13 — LlmDecomposition (prerequisite, done)
- #44 — agentic planning architecture epic (parent)
- #54 — recursive LLM decomposition (follow-on, filed this session)
- #56 — forward reasoning with effects (follow-on, filed this session)
- #57 — task tree construction DSL (follow-on, filed this session)
- #58 — pass failed static method context to LLM fallback (follow-on, filed this session)
