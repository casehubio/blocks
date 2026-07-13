# Routing CBR Enhancements — Design Spec

**Issues:** #37, #34, #32
**Branch:** `issue-37-routing-cbr-enhancements`
**Date:** 2026-07-13

## Context

`CbrAgentRoutingStrategy` selects agents based on historical success patterns from
pre-retrieved CBR experiences (`AgentRoutingContext.experiences()`). Three gaps limit
its effectiveness:

1. **Hardcoded outcome weights** (#37) — `OUTCOME_WEIGHTS` (SUCCESS=1.0, GATE_EXPIRED=0.5,
   GATE_REJECTED=0.25, FAILURE=0.0) is a static constant. Domains cannot tune how different
   outcomes influence worker scoring.

2. **Ignored feature similarity** (#37) — `RetrievedExperience.featureSimilarities()` and
   `similarityScore()` are available but unused. All experiences contribute equally to
   worker scores regardless of how similar they are to the current case.

3. **No enrichment mechanism for non-LLM strategies** (#34) — `RoutingPromptSection` provides
   text enrichment for `LlmAgentRoutingStrategy`. `CbrAgentRoutingStrategy` has no equivalent —
   domain-specific scoring signals cannot reach it.

4. **Single-step analysis only** (#32) — `analyseExperiences()` examines each `ExperiencePlanStep`
   independently. Plan structure (step sequences, predecessor relationships, worker co-occurrence
   patterns) is ignored.

### Architecture note: RoutingFeatureExtractor removal

`RoutingFeatureExtractor` was deleted in commit `b875f89` (engine#505). Feature extraction
moved to the engine's `CbrRetrievalService` via `CbrConfig.featureExtractor()` (JQ expressions
or lambda). Feature *weights* are configured via `CbrConfig.weights()`, passed to
`CbrQuery.withWeights()` at retrieval time. The original #37 framing (add `extractWeights()`
to `RoutingFeatureExtractor`) no longer applies.

Domain implementations (`AmlRoutingFeatureExtractor`, `ClinicalRoutingFeatureExtractor`) are
orphaned code implementing the deleted SPI — they need cleanup in their respective repos.

**#37 scope reframing:** The original #37 framing (add `extractWeights()` to
`RoutingFeatureExtractor`) is obsolete — the deleted SPI's responsibility moved to
engine-side config. Feature weights are now domain-configured via `CbrConfig.weights()`,
applied at retrieval time by `CbrRetrievalService` (confirmed: `CbrQuery.withWeights(config.weights())`).
The remaining #37 gap is on the *analysis* side: how to make the scoring of retrieved
experiences configurable (outcome weights, similarity weighting). That is what this
spec addresses. Issue #37 title/body should be updated to reflect the reframed scope.

## Implementation order

#37 → #34 → #32. Each builds on the previous:
- #37 makes analysis configurable (foundation)
- #34 introduces the enrichment SPI (abstraction)
- #32 implements plan composition as a signal provider (uses #34)

---

## #37 — Configurable Analysis Weights

### 1. CbrOutcomeWeights SPI

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.RoutingOutcome;

public interface CbrOutcomeWeights {
    Map<RoutingOutcome, Double> weights();
}
```

`@DefaultBean` implementation preserves current behaviour:

```java
@DefaultBean
@ApplicationScoped
public class DefaultCbrOutcomeWeights implements CbrOutcomeWeights {
    private static final Map<RoutingOutcome, Double> DEFAULTS = Map.of(
        RoutingOutcome.SUCCESS, 1.0,
        RoutingOutcome.GATE_EXPIRED, 0.5,
        RoutingOutcome.GATE_REJECTED, 0.25,
        RoutingOutcome.FAILURE, 0.0);

    @Override
    public Map<RoutingOutcome, Double> weights() {
        return DEFAULTS;
    }
}
```

Domain repos override with `@ApplicationScoped` (displaces `@DefaultBean` — Pattern C).
`CbrAgentRoutingStrategy` injects `Instance<CbrOutcomeWeights>`, resolves eagerly in
constructor. With `@DefaultBean`, `Instance` is always satisfied — either by the default
or a domain override.

Step outcome lookup converts the `ExperiencePlanStep.stepOutcome()` string to
`RoutingOutcome` via `valueOf()`, defaulting to 0.0 for unrecognised values.

**Vocabulary mismatch (current state):** `CbrCaseRetainObserver.OUTCOME_MAP` maps
`TaskStatus` to step outcome strings that do not fully align with the `RoutingOutcome`
enum. The effective mapping:

| TaskStatus | stepOutcome string | RoutingOutcome | Weight |
|-----------|-------------------|----------------|--------|
| COMPLETED | "SUCCESS" | SUCCESS | 1.0 |
| FAULTED | "FAILURE" | FAILURE | 0.0 |
| REJECTED | "DECLINED" | unrecognised → 0.0 | 0.0 |
| CANCELLED | "CANCELLED" | unrecognised → 0.0 | 0.0 |
| OBSOLETE | "OBSOLETE" | unrecognised → 0.0 | 0.0 |

Until engine#717 aligns `ExperiencePlanStep.stepOutcome()` with `RoutingOutcome`, the
effective step outcome vocabulary from the retain path is {SUCCESS, FAILURE} — DECLINED,
CANCELLED, and OBSOLETE are all treated as zero-weight (same as FAILURE). The
`GATE_EXPIRED` (0.5) and `GATE_REJECTED` (0.25) entries in `DefaultCbrOutcomeWeights`
are valid `RoutingOutcome` enum values but are not currently produced by the retain path
— they will become reachable when engine#717 properly aligns the data flow.

### 2. Experience similarity weighting

Change `analyseExperiences()` to weight each experience's contribution by its
`similarityScore()`. A 0.95-similarity experience contributes ~3x more than a
0.3-similarity one.

Negative similarity scores (anti-similar cases) are clamped to zero — they represent
cases that actively contradict the current case and must not contribute to scoring:

```java
// Before:
stats[0] += outcomeWeight;
stats[1]++;

// After:
double relevance = Math.max(0.0, exp.similarityScore());
stats[0] += outcomeWeight * relevance;
stats[1] += relevance;
```

The weighted average naturally gives more influence to better-matching past cases.
Unconditional — no config flag needed (pre-release, correct by default).

**Tie-breaking update:** After this change, `stats[1]` is a sum of similarity
scores (evidence mass), not an integer count. The current tie-breaker casts to int
(`(int) entry.getValue()[1]`) which loses precision. Change to compare evidence mass
as a raw double:

```java
// Before:
final int total = (int) entry.getValue()[1];
if (rate > bestRate || (rate == bestRate && total > bestCount)) {

// After:
final double evidenceMass = entry.getValue()[1];
if (rate > bestRate || (rate == bestRate && evidenceMass > bestEvidenceMass)) {
```

### 3. analyseExperiences refactoring

Refactor `analyseExperiences()` to return per-candidate scores instead of a single
winner. This is required for signal integration (#34) — the caller must combine
experience scores with signal scores before selecting.

```java
// Before (returns winner name):
private @Nullable String analyseExperiences(
    List<RetrievedExperience> experiences,
    Set<String> eligibleIds, String capabilityName)

// After (returns per-candidate score map):
private Map<String, Double> analyseExperiences(
    List<RetrievedExperience> experiences,
    Set<String> eligibleIds, String capabilityName)
```

Returns an empty map when no matching experiences exist. Each value is the weighted
average score for that candidate (experience outcome weight × similarity, normalised
by evidence mass). The `doSelect()` method combines these with signal scores before
selecting the winner.

### Files changed

| Module | File | Change |
|--------|------|--------|
| blocks | `CbrOutcomeWeights.java` | New interface |
| blocks | `DefaultCbrOutcomeWeights.java` | New `@DefaultBean` |
| blocks | `CbrAgentRoutingStrategy.java` | Inject weights, similarity weighting, return score map |
| blocks | `CbrAgentRoutingStrategyTest.java` | Test configurable weights, similarity weighting, score map |

---

## #34 — RoutingSignalProvider SPI

### 1. RoutingSignal — structured per-candidate data

```java
package io.casehub.api.spi.routing;

public record RoutingSignal(Map<String, CandidateSignal> candidates) {
    public record CandidateSignal(double score, @Nullable String reason) {}
}
```

Per-candidate score with a reason. **All scores must be in [0.0, 1.0]** — this is a
contract requirement enforced by `RoutingSignalAssembler` (log warning and clamp
out-of-range values). Normalised scores make naive addition safe and prevent any
single provider from dominating the combination.

### 2. RoutingSignalProvider — the SPI

```java
package io.casehub.api.spi.routing;

public interface RoutingSignalProvider {
    String id();
    @Nullable RoutingSignal signal(AgentRoutingContext context, List<AgentCandidate> eligible);
}
```

CDI-discovered, `@Priority(N)` for ordering, return `null` when nothing to contribute.
`id()` identifies the signal source — strategies can selectively consume by id.

Implement as `@ApplicationScoped`. Must be thread-safe.

### 3. RoutingSignalAssembler — discovery and collection

```java
package io.casehub.api.spi.routing;

@ApplicationScoped
public class RoutingSignalAssembler {
    private final List<RoutingSignalProvider> providers;

    @Inject
    public RoutingSignalAssembler(Instance<RoutingSignalProvider> providers) {
        this.providers = providers.stream()
            .sorted(Comparator.comparingInt(RoutingSignalAssembler::priority))
            .toList();
    }

    public Map<String, RoutingSignal> assemble(
            AgentRoutingContext context, List<AgentCandidate> eligible) {
        // Iterate providers, call signal(), collect non-null results
        // Keyed by provider id
        // Log + skip on exception (same resilience as RoutingPromptAssembler)
    }
}
```

Returns `Map<String, RoutingSignal>` — strategies see all signal sources, not a
pre-merged result. Failing providers are logged and skipped.

### 4. Integration into CbrAgentRoutingStrategy

Inject `RoutingSignalAssembler`. After experience analysis (which now returns
`Map<String, Double>`), collect signals and combine per candidate:

```
experienceScores = analyseExperiences(context.experiences(), eligibleIds, capabilityName)
signals = assembler.assemble(context, eligible)

for each eligible candidate:
    finalScore = experienceScores.getOrDefault(candidateId, 0.0)
    for each signal:
        candidateSignal = signal.candidates.get(candidateId)
        if candidateSignal != null:
            finalScore += candidateSignal.score

bestCandidate = candidate with highest finalScore (> 0.0)
```

**Sparse signal maps:** Providers MAY return sparse candidate maps — only candidates
the provider has data for need entries. Missing entries contribute +0 to the score
(absence of data, not evaluated-as-zero). Consumers must handle null from
`signal.candidates.get()`.

Signal scores are additive. With normalised [0.0, 1.0] scores from all providers,
the combination is dimensionally consistent. If no experiences AND no signals
produce a positive score, fall through to graph query fallback.

### 5. Symmetry with prompt enrichment

| Text enrichment (LLM) | Structured enrichment (algorithmic) |
|---|---|
| `RoutingPromptSection` | `RoutingSignalProvider` |
| `RoutingPromptAssembler` | `RoutingSignalAssembler` |
| Returns `@Nullable String` | Returns `@Nullable RoutingSignal` |
| Consumed by `LlmAgentRoutingStrategy` | Consumed by `CbrAgentRoutingStrategy` |

### Files changed

| Module | File | Change |
|--------|------|--------|
| engine-api | `RoutingSignal.java` | New record |
| engine-api | `RoutingSignalProvider.java` | New SPI interface |
| engine-api | `RoutingSignalAssembler.java` | New assembler |
| engine-api | `RoutingSignalAssemblerTest.java` | Contract + resilience tests |
| engine-api | `RoutingSignalProviderContractTest.java` | SPI contract tests |
| blocks | `CbrAgentRoutingStrategy.java` | Inject assembler, apply signals |
| blocks | `CbrAgentRoutingStrategyTest.java` | Signal integration tests |

---

## #32 — Plan Composition Matching

### 1. PlanCompositionAnalyser — a RoutingSignalProvider

```java
package io.casehub.blocks.routing.agent;

@ApplicationScoped
public class PlanCompositionAnalyser implements RoutingSignalProvider {

    private final CbrCaseOutcomeWeights caseOutcomeWeights;

    @Inject
    public PlanCompositionAnalyser(
            Instance<CbrCaseOutcomeWeights> caseOutcomeWeights) {
        this.caseOutcomeWeights = caseOutcomeWeights.get();
    }

    @Override
    public String id() { return "plan-composition"; }

    @Override
    public @Nullable RoutingSignal signal(
            AgentRoutingContext context, List<AgentCandidate> eligible) {
        // Analyse plan traces across context.experiences()
        // Return per-candidate plan-fit scores
    }
}
```

### 2. CbrCaseOutcomeWeights — case-level outcome weighting

Case-level outcomes (COMPLETED, FAULTED, CANCELLED, etc.) use a different vocabulary
from step-level outcomes (`RoutingOutcome` enum: SUCCESS, FAILURE, GATE_REJECTED,
GATE_EXPIRED). A separate weight map is required.

```java
package io.casehub.blocks.routing.agent;

public interface CbrCaseOutcomeWeights {
    Map<String, Double> weights();
}
```

String keys because case-level outcomes have no shared enum — they are domain-dependent
strings from `CaseOutcomeEvent.outcomeLabel()`.

```java
@DefaultBean
@ApplicationScoped
public class DefaultCbrCaseOutcomeWeights implements CbrCaseOutcomeWeights {
    private static final Map<String, Double> DEFAULTS = Map.of(
        "COMPLETED", 1.0,
        "FAULTED", 0.2,
        "CANCELLED", 0.0);

    @Override
    public Map<String, Double> weights() { return DEFAULTS; }
}
```

### 3. Analysis dimensions

**Data source:** Multi-step plan traces are populated by the engine's
`CbrCaseRetainObserver` (#703), which retains all terminal capability plan items
for a case at case close. A case with 3 capability bindings that all complete produces
a `PlanCbrCase` with 3 `PlanTrace` entries. These are mapped to
`RetrievedExperience.planTrace()` → `List<ExperiencePlanStep>` by `CbrRetrievalService`.

For each retrieved experience with `planTrace.size() >= 2`:

**Multi-step plan-fit scoring:** For steps matching the target capability, score each
candidate based on the case-level outcomes of the plans they participated in. Step-level
performance is already captured by `analyseExperiences()` — the plan composition signal
contributes the orthogonal dimension of case-level context. This avoids double-counting:
`analyseExperiences()` scores all step outcomes (including multi-step), while
`PlanCompositionAnalyser` scores only the case outcome for multi-step contexts.

**Case outcome weighting:** Weight each experience's contribution by its overall
`outcome` field using `CbrCaseOutcomeWeights` (NOT `CbrOutcomeWeights` — the
vocabularies are distinct). A worker that appeared in COMPLETED cases gets full credit;
a worker that appeared in FAULTED cases is discounted.

**Eligible-set filtering:** Only score candidates present in the eligible set.
Return `null` if no multi-step plan trace data exists or no eligible candidates
appear in any trace.

### 4. Algorithm sketch

```
for each experience where planTrace.size() >= 2:
    caseOutcomeWeight = caseOutcomeWeights.getOrDefault(experience.outcome(), 0.0)
    relevance = Math.max(0.0, experience.similarityScore())
    if relevance == 0.0 or caseOutcomeWeight == 0.0: skip

    targetSteps = planTrace entries matching context.capabilityName()

    for each targetStep:
        if targetStep.workerName not in eligibleIds: skip

        score = caseOutcomeWeight * relevance
        accumulate score for workerName (weightedSum, evidenceMass += relevance)

for each eligible candidate with accumulated evidence:
    planFitScore = weightedSum / evidenceMass
```

### 5. Scope boundaries

- Works with flat `List<ExperiencePlanStep>` — no DAG analysis, no priority ordering
- Analysis only, not plan generation
- Scores for current target capability only — no cross-capability influence
- Returns `null` (no signal) when no experience has `planTrace.size() >= 2` —
  single-step plans have no structural information
- **Future enhancement:** predecessor-specific analysis (scoring candidates based on
  which workers preceded them in successful plans) requires meaningful `priority`
  values in plan traces. Currently `CbrCaseRetainObserver` hardcodes priority=0 for
  all traces. A prerequisite engine change is needed before predecessor analysis can
  be added (see deferred issues).

### Files changed

| Module | File | Change |
|--------|------|--------|
| blocks | `CbrCaseOutcomeWeights.java` | New interface for case-level outcome weights |
| blocks | `DefaultCbrCaseOutcomeWeights.java` | New `@DefaultBean` |
| blocks | `PlanCompositionAnalyser.java` | New `RoutingSignalProvider` implementation |
| blocks | `PlanCompositionAnalyserTest.java` | Unit tests with multi-step plan scenarios |

---

## Data flow — complete picture

```
Retrieved experiences (from engine via CbrConfig)
        │
        ├──→ CbrAgentRoutingStrategy.analyseExperiences()
        │         uses CbrOutcomeWeights (step-level, #37, injectable)
        │         uses similarityScore weighting (#37, clamped ≥ 0)
        │         → Map<String, Double> per-candidate experience scores
        │
        ├──→ PlanCompositionAnalyser (#32, RoutingSignalProvider)
        │         uses CbrCaseOutcomeWeights (case-level, injectable)
        │         scores case-level context for multi-step planTrace structures
        │         (step-level scoring handled by analyseExperiences — no overlap)
        │         → RoutingSignal with per-candidate plan-fit scores [0.0, 1.0]
        │
        └──→ (future RoutingSignalProvider implementations)
                  │                     scores normalised to [0.0, 1.0]
                  ▼
        RoutingSignalAssembler (#34)
        collects all signals, keyed by provider id
                  │
                  ▼
        CbrAgentRoutingStrategy combines:
            finalScore = experienceScore + sum(signal scores)
            selects highest-scoring candidate (> 0.0)
                  │
                  ▼
        RoutingResult (best candidate, or graph query fallback)
```

## Deferred issues

The following items are out of scope for this spec and must be filed as separate issues:

1. **`ExperiencePlanStep.stepOutcome()` should return `RoutingOutcome`** (engine-api) —
   engine#717. The Javadoc already says the value is "one of `RoutingOutcome` enum values,
   stored as the enum's `name()` string." Changing the type to `RoutingOutcome` directly
   would eliminate the string↔enum conversion.

2. **`CbrCaseRetainObserver` should set meaningful plan trace priorities** (engine) —
   engine#718. Currently hardcodes `priority = 0` for all `PlanTrace` entries. Meaningful
   priorities are needed before predecessor-specific analysis can be added.

3. **Predecessor-specific plan composition analysis** (blocks) — blocks#53. Enhancement
   to `PlanCompositionAnalyser` that scores candidates based on predecessor patterns.
   Blocked by engine#718.

## CLAUDE.md updates needed

After implementation:
- Remove `RoutingFeatureExtractor` and `TextOnlyFeatureExtractor` from the package table
- Remove `CbrRoutingOutcomeRecorder` from the package table
- Add `CbrOutcomeWeights`, `DefaultCbrOutcomeWeights`
- Add `CbrCaseOutcomeWeights`, `DefaultCbrCaseOutcomeWeights`
- Add `RoutingSignal`, `RoutingSignalProvider`, `RoutingSignalAssembler`
- Add `PlanCompositionAnalyser`
- Update consumers table: remove AmlRoutingFeatureExtractor/ClinicalRoutingFeatureExtractor references
