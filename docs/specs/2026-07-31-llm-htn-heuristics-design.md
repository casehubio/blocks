# LLM-Generated HTN Heuristics — Design Spec

**Issue:** casehubio/blocks#47
**Date:** 2026-07-31
**Branch:** issue-47-llm-htn-heuristics
**Parent epic:** #44 (agentic planning architecture)

## Problem

`StaticDecomposition` uses first-match method selection — it iterates methods
in declaration order and picks the first whose guard passes. When multiple
methods have passing guards, the first always wins regardless of plan quality.
There is no mechanism to rank or prefer one method over another based on
context.

The Pytrich paper ([arxiv 2605.07707v1](https://arxiv.org/html/2605.07707v1))
demonstrates that LLM-generated heuristic functions can guide HTN planners
to solve 131/139 benchmark problems with 83% fewer node expansions — while
soundness remains guaranteed by the planner, not the LLM.

## Approach

Introduce a `DecompositionHeuristic<T>` SPI for scoring decomposition methods,
and a `HeuristicDecomposition<T>` strategy that uses it for ranked method
selection with backtracking. Three heuristic implementations: structural cost
analysis (zero-LLM baseline), LLM-based online evaluation, and weighted
composite.

The design separates three concerns:
1. **How to score methods** — the `DecompositionHeuristic` SPI (pluggable)
2. **How to use scores during decomposition** — `HeuristicDecomposition` (ranked + backtracking)
3. **How to generate scores** — implementations (structural, LLM, composite)

## Design

### Core SPI

```java
@FunctionalInterface
public interface DecompositionHeuristic<T> {
    Uni<List<ScoredMethod<T>>> evaluate(
        TaskNode.CompoundTask<T> task,
        List<DecompositionMethod<T>> methods,
        DecompositionContext<T> context);
}

public record ScoredMethod<T>(DecompositionMethod<T> method, double score) {}
```

**Batch-native:** receives all eligible methods at once. LLM evaluators send
one prompt and get rankings back. Per-method evaluators wrap trivially.

**Async (`Uni`):** LLM evaluation is async. Structural evaluation wraps
synchronous code via `Uni.createFrom().item()`. Fits the `DecompositionStrategy`
pipeline which already returns `Uni`.

**Higher score = better:** consistent with CaseHub's routing architecture
(`RoutingSignalProvider`, CBR scoring). Cost-based heuristics negate.

**Guard-filtered input:** the strategy filters methods by guard BEFORE calling
the heuristic. The heuristic only ranks methods that are structurally
applicable.

**Completeness contract:** the returned list MUST contain exactly one
`ScoredMethod` per input method. Implementations that cannot meaningfully
score a method must still include it (e.g. with score 0.0).
`CompositeHeuristic` validates this invariant at runtime. This eliminates
ambiguity about missing scores — there are none.

### Prerequisite: Context-Carried Decomposer

`SequenceStrategy.decompose()` currently hardcodes `new StaticDecomposition<T>()`
for child resolution. This means nested compound tasks within a sequence are
always decomposed with first-match semantics regardless of the root strategy.
`ForwardReasoningDecomposition` already works around this by checking
`instanceof SequenceStrategy` and iterating `children()` directly.

Rather than propagating this pattern to `HeuristicDecomposition`,
`AgenticDecompositionContext` gains a `decomposer` field:

```java
public record AgenticDecompositionContext<T>(T state, List<RoutingCandidate> agents, int depth,
                                             @Nullable String staticFailureHint,
                                             @Nullable DecompositionStrategy<T> decomposer)
        implements DecompositionContext<T> { ... }
```

`SequenceStrategy.decompose()` uses the context-carried decomposer when
present, falling back to `StaticDecomposition` when null:

```java
var decomposer = (ctx instanceof AgenticDecompositionContext<T> adc && adc.decomposer() != null)
    ? adc.decomposer() : new StaticDecomposition<T>();
```

**Effect on existing strategies:**

- `StaticDecomposition` — passes context through unchanged (no decomposer
  set). `SequenceStrategy` falls back to `StaticDecomposition`. No behavior
  change.
- `HybridDecomposition` — same: passes context through unchanged.
- `ForwardReasoningDecomposition` — retains its `instanceof SequenceStrategy`
  check. That check serves a fundamentally different purpose: accessing
  `children()` for synchronous effect-interleaved expansion. The async
  `decompose()` callback cannot thread mutable state for effect application.
- `HeuristicDecomposition` — sets `this` as the decomposer in the context.
  `SequenceStrategy` delegates child decomposition back through
  `HeuristicDecomposition`, applying heuristic ranking at every level.
  No `instanceof SequenceStrategy` check needed.

### Strategy: `HeuristicDecomposition<T>`

```java
public class HeuristicDecomposition<T> implements DecompositionStrategy<T> {
    private final DecompositionHeuristic<T> heuristic;

    @Override public String id() { return "heuristic"; }
}
```

**Decomposition algorithm:**

1. Leaf task → `DagPlan.singleton(leaf)`
2. Compound task → filter methods by guard
3. 0 eligible → `NoMethodMatchedException`
4. 1 eligible → expand directly (fast path, no heuristic invocation)
5. 2+ eligible → score with heuristic, sort descending by score
6. Try ranked methods with backtracking:
   - Expand best-scored method (recursively through children)
   - If expansion succeeds → return the plan
   - If `NoMethodMatchedException` from downstream → try next-ranked
   - All methods exhausted → `NoMethodMatchedException`

**Recursive heuristic propagation:** `HeuristicDecomposition` sets itself as
the decomposer in `AgenticDecompositionContext` before calling any method's
strategy. When a method's strategy is `SequenceStrategy`, the sequence
delegates child decomposition back through the context-carried decomposer —
applying heuristic ranking at every level, not just the root. No `instanceof
SequenceStrategy` check is needed in `HeuristicDecomposition`. For opaque
strategies, the method's own strategy handles decomposition directly.

**Backtracking scope:** if a method's subtree throws `NoMethodMatchedException`
N levels deep, it is caught at the current level and the next-ranked method is
tried. Planning has no side effects — no state is mutated, so abandoning a
partial expansion is structurally safe. However, when subtrees contain
`LlmDecomposition` or `HybridDecomposition`, the discarded LLM calls have
real latency and token cost. In the worst case with N root methods and M
child methods, backtracking may explore up to N × M subtrees. Backtracking
is computationally free for structural strategies but not for LLM-involving
sub-strategies.

**Forward reasoning deferred:** `ForwardReasoningDecomposition` threads mutable
state synchronously through expansion. Heuristic evaluation is async. These
don't compose cleanly without design compromise. The combination is a future
extension:

| | First-match | Heuristic-ranked |
|---|---|---|
| No effects | `StaticDecomposition` | `HeuristicDecomposition` |
| With effects | `ForwardReasoningDecomposition` | Future |

### Implementation: `StructuralCostHeuristic<T>`

Zero-LLM baseline that estimates plan cost from task tree structure.

```java
public class StructuralCostHeuristic<T> implements DecompositionHeuristic<T> {
    private final double opaqueCost;  // default cost for opaque strategies
}
```

**Algorithm:** for each method, estimate total leaf task count:

- `LeafTask` → cost 1
- `SequenceStrategy` children → recursively sum costs
- Nested `CompoundTask` → minimum cost across its methods (optimistic: assumes
  best method chosen downstream)
- Opaque strategy → `opaqueCost` (configurable, default 1.0)

Score = `-totalCost` (lower cost → higher score).

The `SequenceStrategy` case accesses `children()` directly for structural
tree-walking. This is data access for cost estimation, distinct from the
decomposer coupling addressed by the context-carried decomposer pattern.

Matches the paper's strongest heuristic approach: precomputed
minimum-decomposition cost table where "primitive actions receive cost 1,
abstract tasks iteratively converge to `min(sum(subtask costs) for each method)`."

Synchronous implementation — tree-walking only, no I/O. Wrapped in
`Uni.createFrom().item()`.

### Implementation: `LlmDecompositionHeuristic<T>`

LLM evaluates methods online — one call per compound task with 2+ eligible methods.

```java
public class LlmDecompositionHeuristic<T> implements DecompositionHeuristic<T> {
    private final AgentProvider agentProvider;
    private final Function<T, String> stateRenderer;
}
```

**Prompt construction:** auto-generates method descriptions from tree structure
(no `DecompositionMethod.name()` needed):

```
You are evaluating decomposition methods for the task: "{taskName}"

Current state:
{stateRenderer output}

Eligible methods:
1. {subtask1} → {subtask2} → {subtask3}
2. {subtask1} → {subtask2}

Score each method 0.0–1.0 based on how well it fits the current state.
Respond with JSON only: [{"method": 1, "score": 0.8}, {"method": 2, "score": 0.4}]
```

Method descriptions extracted from tree structure:
- `SequenceStrategy` → child names/descriptions joined with ` → `
- `CompoundTask` children → `name()`
- `PlannedTask` / `PrimitiveTask` → `description()`
- Opaque strategies → `"(compound strategy)"`

**Response parsing:** defensive — strip markdown fences, parse JSON array,
handle missing fields. Unscored methods default to 0.0.

**Failure handling:** if the LLM call fails (network error, timeout, unparseable
response), log a warning and return all methods with equal scores (0.0) —
preserving declaration order as fallback. The strategy still tries them in
order and backtracks on failure, so a heuristic failure degrades to
first-match behavior rather than failing the decomposition.

**Online vs. offline:** this is the online approach (LLM per invocation). The
`DecompositionHeuristic` SPI supports both patterns — an offline implementation
would precompute and cache scores per domain. That is a future extension.

### Implementation: `CompositeHeuristic<T>`

Combines multiple heuristics via normalized weighted sum.

```java
public class CompositeHeuristic<T> implements DecompositionHeuristic<T> {
    private final List<WeightedHeuristic<T>> delegates;

    public record WeightedHeuristic<T>(DecompositionHeuristic<T> heuristic, double weight) {}
}
```

**Algorithm:**

1. Call each delegate with the same `(task, methods, context)`
2. Per delegate, validate completeness: assert returned list size equals
   input method count. Fail fast on violation — this is an SPI contract
   breach, not a recoverable condition.
3. Per delegate, normalize scores to [0, 1]:
   `normalized = (score - min) / (max - min)` (all-equal → 0.5)
4. Per method, compute: `compositeScore = Σ(weight_i × normalized_i) / Σ(weight_i)`
5. Return methods with composite scores

Normalization prevents scale dominance — structural cost returns negative
integers while LLM returns [0, 1]. Without normalization, larger absolute
values dominate regardless of weight.

### DSL Integration

Add to `Decomposition.java`:

```java
public static <T> HeuristicDecomposition<T> heuristic(DecompositionHeuristic<T> heuristic) {
    return new HeuristicDecomposition<>(heuristic);
}
```

## File Inventory

All files in `io.casehub.blocks.agentic.decomposition`:

| File | Type |
|------|------|
| `DecompositionHeuristic.java` | SPI interface |
| `ScoredMethod.java` | Record |
| `HeuristicDecomposition.java` | Strategy |
| `StructuralCostHeuristic.java` | Heuristic impl |
| `LlmDecompositionHeuristic.java` | Heuristic impl |
| `CompositeHeuristic.java` | Heuristic impl |
| `HeuristicDecompositionTest.java` | Tests |
| `StructuralCostHeuristicTest.java` | Tests |
| `LlmDecompositionHeuristicTest.java` | Tests |
| `CompositeHeuristicTest.java` | Tests |

## Testing Strategy

All tests are plain JUnit 5 with Mockito. No CDI container.

**`HeuristicDecompositionTest`:**
- Single eligible method → fast path, heuristic not invoked
- Multiple eligible methods → heuristic called, best-scored expanded
- Backtracking: best-scored fails downstream → tries next-ranked
- All methods fail → `NoMethodMatchedException`
- No eligible methods → `NoMethodMatchedException`
- Recursive propagation through nested `SequenceStrategy` children
- Opaque strategies: delegates without heuristic propagation

**`StructuralCostHeuristicTest`:**
- `SequenceStrategy` methods → cost = leaf count
- Nested compound tasks → minimum cost across sub-methods
- Opaque strategies → configurable default cost
- Single method → returns scored list

**`LlmDecompositionHeuristicTest`:**
- Well-formed LLM response → parsed scores
- Malformed response → graceful degradation (default scores)
- Markdown-fenced response → stripped and parsed
- State renderer integration → state appears in prompt

**`CompositeHeuristicTest`:**
- Two delegates with different scales → normalization produces correct ranking
- All-equal scores from one delegate → normalized to 0.5
- Weighted combination → weights respected
- Single delegate → passthrough
- Delegate returns fewer scores than methods → fails with clear error

## Non-Goals

- **Forward reasoning + heuristic combination:** deferred (async/sync tension)
- **Offline heuristic generation:** the SPI supports it; implementation deferred
- **`DecompositionMethod.name()` field:** would improve LLM prompts and
  eliminate strategy-inspection logic in `LlmDecompositionHeuristic`, but
  requires a cross-repo engine-api change (`DecompositionMethod` is a record
  in casehub-engine-api). Auto-generated descriptions from strategy tree
  structure are sufficient for this feature's research scope. Tracked as
  explicit design debt: casehubio/blocks#75
- **Global search (A\*/GBFS):** would be a separate `DecompositionStrategy`;
  greedy-with-backtracking captures most value (paper shows GBFS outperforms A\*)
- **Beam search / parallel exploration:** future extension

## References

- [HTN Planning with LLM-Generated Heuristics](https://arxiv.org/html/2605.07707v1) (May 2026)
- Research survey: `docs/research/2026-07-09-task-decomposition-and-agent-planning-landscape.md` §3.7, §6.4h
- Parent epic: casehubio/blocks#44
