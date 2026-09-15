# Cognitive Pipeline Health — Design Spec

**Issue:** casehubio/blocks#279 (broadened from strategy learning to full pipeline audit)
**Date:** 2026-09-15
**Scope:** Fix all "wired but not flowing" gaps across 8 cognitive orchestrators, add LLM proof tests

## Problem

The cognitive pipeline (CognitionCore → orchestrators → PromptSections) has structural gaps that prevent several subsystems from contributing to the showcase conversation. An audit of all 8 orchestrators revealed:

| Orchestrator | Status | Prompt contributes? | Root blocker |
|---|---|---|---|
| Mood | WORKING | Yes | — |
| MentalModel | WORKING | Yes | Heuristic path works; LLM inference blocked but non-critical |
| Drives | PARTIAL | Lopsided | CompetenceDrive=0 (cascading from Strategy), CuriosityDrive=baseline |
| UserModel | PARTIAL | Heuristic only | All signals NEUTRAL, LLM synthesis late, 1hr cooldown |
| Narrative | PARTIAL | Yes | CognitionStack bypass, not production pipeline |
| Strategy | GAP | Never | Signals never batch, reflect() never called, no seeding |
| Goals | GAP | Rarely | Cascading from Strategy, 60min cooldown, no CuriosityGoalMapper |
| MemoryHygiene | N/A | No section | Not wired in CognitionStack |

**Cascade chain:** Strategy broken → CompetenceDrive stuck → Goals blocked.

**Common structural pattern:** 1-signal-per-tick prevents batch thresholds from being reached. Each tick drains the signal buffer, but only 1 signal arrives between ticks, so batch-dependent processing never fires.

**Compile break:** ConversationRunner calls `recordInteraction()` with 5 args, but CognitionCore (post-#282) requires 6 args with `@Nullable CognitiveImpact`.

## Solution

### 1. Signal correlation + ConversationOutcome checkpoints (D1)

**Problem:** `CognitionCore.recordInteraction()` creates each `EngagementEvent` with a random UUID as `caseId`. `StrategyLearningOrchestrator.tick()` matches `TurnOutcome` signals to `ConversationOutcome` signals by `conversationId == caseId`. Random UUIDs mean turns can never be correlated with a conversation.

**Fix in CognitiveImpact — add conversationId field:**
```java
public record CognitiveImpact(
    @Nullable InteractionSignal userModelSignal,
    @Nullable MoodSignal moodSignal,
    boolean suppressBdiExtraction,
    @Nullable EngagementSignal strategySignal,
    @Nullable String conversationId) { ... }
```
CognitionCore's default EngagementEvent construction uses `impact.conversationId()` as caseId when non-null, random UUID when null. This keeps EngagementEvent construction in one place — ConversationRunner doesn't need to know EngagementEvent's 15-parameter constructor.

**Fix in StrategyLearningOrchestrator — persistent turn accumulation:**
The current `doTick()` eagerly drains ALL pending turns on every call. Since tick runs every turn and only 1 signal arrives between ticks, `drainedTurns` is always size 1. When ConversationOutcome finally arrives, all previous turns have been individually drained and are gone — the matching logic never sees them.

Fix: maintain a `conversationTurns` map (`ConcurrentHashMap<String, List<TurnEntry>>`) that accumulates turns by conversationId across ticks. `doTick()` continues to drain pending turns for stats (engagement rate, sentiment), but also appends each turn with a non-null conversationId to the accumulation map. When a ConversationOutcome arrives, look up the accumulated turns for that conversationId, form a CBR case from ALL of them, then clear the entry:
```java
// In doTick(), after draining pending turns:
for (var entry : drainedTurns) {
    var caseId = entry.signal.event().caseId();
    if (caseId != null) {
        conversationTurns.computeIfAbsent(caseId, k -> new ArrayList<>()).add(entry);
    }
}

// When processing ConversationOutcome:
for (var convEntry : drainedConversations) {
    var accumulated = conversationTurns.remove(convEntry.signal.conversationId());
    if (accumulated == null || accumulated.isEmpty()) continue;
    var features = extractFeatures(accumulated, convEntry.subjectId, state.agentId);
    // ... store CBR case from accumulated turns
}
```

**Fix in ConversationRunner:**
- Generate a stable conversation ID at the start of `run()` (e.g., `tenantId + "-conv-" + UUID`).
- Pass it via `CognitiveImpact` on every `recordInteraction()` call so all turns share the same conversationId.
- At conversation end, record a `ConversationOutcome` signal with the same conversationId, then run a **final tick** to process the outcome:
  ```java
  // After the for loop:
  strategy.record(new EngagementSignal.ConversationOutcome(
      conversationId, "Conversation summary", turnCount),
      agentId, subjectId, tenantId);
  cognition.tick(agentId, tenantId, descriptor, otherSpeakers);  // Process the outcome
  ```
- For this branch, a "conversation" is the full ConversationRunner run. Platform SPI for conversation boundary detection is a follow-on.

**Fix compile breaks:** Two compile breaks from #282:
1. ConversationRunner calls 5-arg `recordInteraction()` — update to 6-arg with `CognitiveImpact` carrying conversationId.
2. `CognitionStack.tick()` passes `Set<String>` where `SubjectResolver` is required — wrap as lambda: `(a, t) -> activeSubjects`.

### 2. Auto-trigger reflect() asynchronously in tick() (D2)

**Problem:** `reflect()` is the only method that produces `StrategyProfile` with guidelines, but nobody calls it.

**Fix in StrategyLearningOrchestrator.tick():**
After storing one or more CBR cases (the `Learned` outcome), check if reflection should fire:
```java
if (casesStored > 0) {
    int totalCases = countAgentCases(state.agentId, state.tenantId);
    boolean enoughCases = totalCases >= config.minCasesForReflection();
    boolean cooldownElapsed = state.lastReflectTimestamp == null
        || Duration.between(state.lastReflectTimestamp, clock.instant())
            .compareTo(config.staleStateTimeout()) > 0;
    if (enoughCases && cooldownElapsed) {
        state.lastReflectTimestamp = clock.instant();  // Claim slot before releasing lock
        REFLECT_EXECUTOR.submit(() -> doReflectAsync(state.agentId, state.tenantId));
    }
}
```

**Dedicated executor + lock-free LLM call:**
```java
private static final ExecutorService REFLECT_EXECUTOR =
    Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "strategy-reflect");
        t.setDaemon(true);
        return t;
    });
```

The existing `ReentrantLock` shared between `tick()` and `reflect()` causes lock contention — if reflect holds the lock during its LLM call (5-30s), the next tick blocks for the full duration, defeating the purpose of async.

Fix: `doReflectAsync()` acquires the lock briefly to snapshot the data it needs (cases, current profile), releases it, runs the LLM call without holding any lock, then re-acquires briefly to store the result:
```java
private void doReflectAsync(String agentId, String tenantId) {
    try {
        // 1. Snapshot under lock (fast)
        StrategyProfile profile;
        List<ScoredCbrCase<CbrCase>> cases;
        var lock = tickLocks.get(stateKey(agentId, tenantId));
        lock.lock();
        try {
            profile = currentStrategy(agentId, tenantId).orElseGet(
                () -> defaultProfile(agentId, tenantId));
            cases = queryCases(agentId, tenantId);
        } finally { lock.unlock(); }

        // 2. LLM call — no lock held (slow)
        var result = synthesiseGuidelines(profile, cases);

        // 3. Store result under lock (fast)
        lock.lock();
        try { applyResult(result, agentId, tenantId); }
        finally { lock.unlock(); }
    } catch (Exception e) {
        LOG.log(Level.WARNING, "Async reflect failed for " + agentId, e);
    }
}
```

**Why not common pool:** `CompletableFuture.runAsync()` without an executor uses `ForkJoinPool.commonPool()`, which is designed for CPU-bound fork-join tasks. A 5-30s blocking LLM call starves the pool and silently swallows exceptions. The dedicated single-thread daemon executor bounds concurrency (one reflect at a time), logs exceptions, and shuts down cleanly with the JVM.

### 3. Neurocortex priming for cold-start (D3)

**Problem:** With no stored cases, reflect() never fires, so StrategyPromptSection never contributes.

**Fix — prime CBR store at CognitionStack construction:**

When `CognitionStack.from()` creates the `StrategyLearningOrchestrator` (at `Stage.REAL_DRIVES+`), also prime the CBR store with synthetic interaction cases derived from the agent's descriptor:

```java
private static void primeStrategyCases(CbrCaseMemoryStore store,
                                        AgentDescriptor descriptor,
                                        StrategyLearningConfig config) {
    var constraints = descriptor.constraints();
    if (constraints == null || constraints.isEmpty()) return;

    // Generate varied synthetic cases from constraints
    // Each case models a different interaction scenario
    // with deliberately varied feature vectors for trend analysis
    for (int i = 0; i < config.minCasesForReflection(); i++) {
        var features = buildSyntheticFeatures(descriptor, i);
        var summary = buildCaseSummary(constraints, i);
        var cbrCase = new FeatureVectorCbrCase(
            summary, "-", null, null, features, null, descriptor.name());
        store.store(cbrCase, config.engagementCaseType(),
            descriptor.name(), config.memoryDomain(), "showcase", null, Path.root());
    }
}
```

**TenantId handling:** Priming happens at `CognitionStack.from()` before any tick, so the tenantId isn't known yet. Fix: prime lazily on the first tick — `CognitionStack.tick()` checks a `primed` flag and calls `primeStrategyCases()` on the first invocation, when tenantId is available from the tick parameters. This ensures primed cases match the actual tenant used for retrieval.

**Variance strategy — complete synthetic feature vector:**
Each of `minCasesForReflection` (default: 5) synthetic cases has the full 12-field feature schema matching `extractFeatures()`:

| Feature | Case 0 | Case 1 | Case 2 | Case 3 | Case 4 | Derivation |
|---------|--------|--------|--------|--------|--------|------------|
| `subjectId` | "synthetic" | "synthetic" | "synthetic" | "synthetic" | "synthetic" | Fixed |
| `agentId` | descriptor.name() | same | same | same | same | From descriptor |
| `conversationTimestamp` | now-4h | now-3h | now-2h | now-1h | now | Linear spacing |
| `turnCount` | 4 | 6 | 8 | 5 | 7 | Varied (4-8) |
| `avgResponseLength` | 80 | 150 | 250 | 120 | 200 | Linear ramp with variation |
| `continuationRate` | 0.6 | 0.7 | 0.8 | 0.75 | 0.85 | Upward trend |
| `meanAffectShift` | -0.05 | 0.05 | 0.15 | 0.1 | 0.2 | Upward trend |
| `avgSnapshot_verbosity` | V | V | V | V | V | From descriptor constraints |
| `avgSnapshot_formality` | F | F | F | F | F | From descriptor constraints |
| `avgSnapshot_initiative` | 0.5 | 0.5 | 0.5 | 0.5 | 0.5 | Default |
| `avgSnapshot_directness` | 0.5 | 0.5 | 0.5 | 0.5 | 0.5 | Default |
| `avgSnapshot_questionRate` | 0.5 | 0.5 | 0.5 | 0.5 | 0.5 | Default |

Dimension derivation from constraints: `precision-in-physics` → formality=0.8, verbosity=0.3. `artistic-expression` → formality=0.4, verbosity=0.7. Unmapped constraints use default 0.5.

The upward trends in `continuationRate` and `meanAffectShift` model an agent whose interaction effectiveness improves over time — giving TrendAnalyzer positive slope signals for the reflection prompt.

**First tick:** The lazy priming detects enough primed cases, the auto-reflect (from fix 2) fires async reflect. The LLM synthesises initial guidelines from the primed memories. By turn 2, `StrategyPromptSection` renders guidelines.

### 4. UserModel quality from mood appraisal (D4)

**Problem:** `CognitionCore.recordInteraction()` hardcodes `QualitySignal.NEUTRAL` for every interaction signal to UserModel.

**Fix in CognitionCore.recordInteraction():**
When `impact == null || impact.userModelSignal() == null` (default path), derive quality from the mood appraisal just recorded:

```java
QualitySignal quality = QualitySignal.NEUTRAL;
var currentMood = mood.currentMood(agentId, tenantId);
if (currentMood.isPresent()) {
    var m = currentMood.get();
    double pleasure = m.pleasure();
    double arousal = m.arousal();
    if (arousal > 0.3 && pleasure > 0.1) {
        quality = QualitySignal.POSITIVE;   // engaged + positive
    } else if (arousal < -0.1 && pleasure < -0.1) {
        quality = QualitySignal.NEGATIVE;   // bored + negative
    }
}
var signal = new InteractionSignal.CustomSignal(userMessage, quality);
```

**Timing caveat:** `mood.currentMood()` returns the mood state from the previous tick, not from the appraisal just recorded for the current interaction. The just-recorded signal sits in the pending queue until the next `tick()`. In the ConversationRunner flow: tick() runs first (updates currentMood), then recordInteraction() records a new mood signal AND reads currentMood — the read reflects the state after the previous tick, not the current interaction. This 1-turn lag is acceptable: mood changes incrementally, so the quality signal reflects the mood trajectory. Document in code.

**Why pleasure+arousal:** Pleasure alone conflates emotional valence with relationship quality. Challenging intellectual debates (Tesla vs Leonardo) produce low pleasure (cognitive strain) but high arousal (engagement). High arousal + positive pleasure = genuine positive quality. Low arousal + negative pleasure = boredom/disengagement = genuine negative quality. The CognitiveImpact.userModelSignal() path (#282) remains the explicit override for consumers needing full control.

### 5. Goals — cooldown, CuriosityDrive chain, and missing mapper (D5)

**Problem 5a — 60-minute cooldown:** `GoalProposalConfig.defaults().cooldown()` is `Duration.ofMinutes(60)`. The showcase completes in minutes.

**Fix:** `GoalProposalConfig` exposes `cooldown` as `Duration`. The YAML cognition config passes through to `CompiledCognition.goalProposal()`. Set the cooldown to `Duration.ofSeconds(30)` in the showcase's `cognition.yaml` (or in CognitionStack.from() if the YAML field isn't wired yet). 30 seconds allows goal proposals to evolve across the 5-15 minute showcase duration while still preventing per-tick churn.

**Problem 5b — CuriosityDrive not wired:** `CognitionStack.from()` uses a flat lambda `(a,t) -> 0.5` for curiosity instead of the real `CuriosityDrive`. The real implementation requires `MemoryHygieneOrchestrator`, which is passed as `null` to `CognitionCore`.

**Fix — full wiring chain:**
`MemoryHygieneOrchestrator` has an 11-parameter constructor. For test-scope wiring, use sensible defaults for parameters that don't matter in the showcase:
```java
// In CognitionStack.from(), at REAL_DRIVES+ stage:
var memoryHygiene = new MemoryHygieneOrchestrator(
    cbrStore,                                              // CBR store (shared)
    new CompositeConfidenceScorer(List.of(                 // confidence scoring
        new WeightedScorer(new ArousalScorer(), 0.5),
        new WeightedScorer(new SurpriseScorer(), 0.5))),
    TemporalDecay.defaults(),                              // temporal decay
    ScopeDecay.defaults(),                                 // scope decay
    null,                                                  // no consolidation summariser
    config.strategyLearning().memoryDomain(),               // memory domain
    List.of(config.strategyLearning().engagementCaseType()), // case types
    RetentionConfig.defaults(),                             // retention config
    10,                                                     // consolidation batch size
    0.7,                                                    // cross-link threshold
    event -> {});                                           // no-op event sink
var curiosityDrive = new CuriosityDrive(memoryHygiene);

// Pass memoryHygiene to CognitionCore (instead of null)
var core = new CognitionCore(mood, drives, userModel, mentalModel,
    strategy, narrative, goals, memoryHygiene, agentProvider);
```
Constructor parameters verified against `MemoryHygieneOrchestrator.java:53-67`. `WeightedScorer` wraps `ConfidenceScorer` implementations (CompositeConfidenceScorer takes `List<WeightedScorer>`, not `List<ConfidenceScorer>`).

At `Stage.FULL`, also wire `CuriosityGoalMapper`:
```java
var mapperList = List.<DriveGoalMapper>of(
    new CuriosityGoalMapper(curiosityDrive),  // NEW
    new CompetenceGoalMapper(competence),
    new AffiliationGoalMapper(affiliation, 0.3, Duration.ofHours(1)),
    new AutonomyGoalMapper(autonomy, 0.5));
```

### 6. LLM proof tests (D6)

**Per-orchestrator tests** in `HistoricalEncounterLlmTest`, complementing existing stage tests:

| Test | Asserts | Turns |
|------|---------|-------|
| `strategyContributesByTurn4()` | StrategyPromptSection returns non-null text by turn 4 (primed cases → async reflect → guidelines) | 4 |
| `moodEvolvesAcrossTurns()` | MoodPromptSection shows different PAD values at turn 1 vs turn 6 | 6 |
| `mentalModelFormsBeliefsAndDesires()` | MentalModelPromptSection renders beliefs + desires for the other agent by turn 4 | 4 |
| `userModelDifferentiatesQuality()` | UserModelPromptSection shows non-NEUTRAL quality signals by turn 6 | 6 |
| `drivesReflectRealSources()` | DrivePromptSection shows non-zero competence and curiosity by turn 6 | 6 |
| `goalsProposedByTurn6()` | GoalPromptSection renders at least one goal proposal by turn 6 | 6 |
| `narrativeFormsEpisodesAndThemes()` | NarrativePromptSection renders episodes + theme by turn 6 | 6 |

**Nondeterminism handling:** Use soft assertions with retry tolerance. Each test runs the conversation twice; assert the pipeline contributes in at least 1 of 2 runs. This bounds flakiness while proving the pipeline works.

**Cost:** ~7 tests × 2 runs × 4-6 turns × ~3 LLM calls/turn = ~250-500 LLM calls. Run under `-Pllm` on demand, not every CI build.

### 7. Additional fixes (no design decision needed)

- **CompetenceDrive cascade:** Automatically unblocked by fixes 1+2+3. Once strategy produces a profile, `engagementTrend()` returns data, CompetenceDrive computes real intensity.
- **CognitiveImpact.fromText():** Update the convenience factory to include the new `conversationId` field (null default).

## Dependency order

```
Fix 1 (signal correlation) → Fix 2 (async auto-reflect) → Fix 3 (priming)
                                                          ↗
Fix 4 (quality signals) ————————————————————————————————→ Fix 6 (tests)
Fix 5 (goals chain) ———————————————————————————————————→
Fix 7 (compile break) — first, enables all other fixes
```

Fix 7 (compile break) goes first — nothing else compiles without it.
Fixes 1→2→3 are the strategy cascade chain.
Fixes 4, 5 are independent.
Fix 6 (tests) comes last — proves everything works.

## Testing

Plain JUnit 5 (no Quarkus runtime, per project convention) for unit tests of the modified orchestrator methods. LLM tests under `-Pllm` profile for end-to-end proof.

**Unit tests (no LLM):**
- `StrategyLearningOrchestratorTest` — verify tick() auto-triggers async reflect when case count crosses threshold; verify ConversationOutcome matching with shared conversationId
- `CognitionCoreTest` — verify recordInteraction with conversationId sets caseId correctly; verify quality derivation from mood pleasure+arousal
- `GoalProposalOrchestratorTest` — verify turn-based or short cooldown allows sequential proposals
- `CognitionStackTest` — verify MemoryHygieneOrchestrator is wired; verify CuriosityDrive + CuriosityGoalMapper construction at FULL stage; verify synthetic case priming populates CBR store

**LLM tests (-Pllm):**
- Per-orchestrator proof tests as specified in Fix 6

## Migration

One API change: `CognitiveImpact` record gains a `@Nullable String conversationId` field. All existing callers passing `null` are unaffected (source compatible). Callers using positional constructors need the additional null arg.

Internal changes:
- `CognitionCore.recordInteraction()` — use conversationId from CognitiveImpact, quality derivation from mood
- `StrategyLearningOrchestrator` — persistent turn accumulation map, async auto-reflect with dedicated executor and lock-free LLM call
- `CognitionStack` — wire MemoryHygieneOrchestrator (11-param constructor), CuriosityDrive, CuriosityGoalMapper, lazy CBR priming, fix SubjectResolver lambda wrapping
- `ConversationRunner` — stable conversation ID, CognitiveImpact construction, ConversationOutcome emission, final tick

## Follow-on issues

- **#283** — Directive-minimal architecture: shift cognitive data from briefing to neurocortex. This branch demonstrates the principle for strategy (primed memories → emergent guidelines). #283 generalises to goals, beliefs, and the full knowledge import pipeline.
- **Conversation boundary SPI** — Platform contract for conversation boundary detection and ConversationOutcome emission. Production consumers need this; the showcase uses a simple "one conversation = one run" contract.
- **Adaptive quality strategy** — A/B testing for UserModel quality signal derivation. When mood-derived quality isn't differentiating well, switch to InteractionMapper path.
- **Strategy dimension extensibility** — Make DEFAULT_DIMENSIONS configurable via StrategyLearningConfig instead of hardcoded.

## References

- `StrategyLearningOrchestrator.java:40-606` — full strategy orchestrator (signal recording, tick, reflect)
- `StrategyPromptSection.java:8-25` — prompt rendering from StrategyProfile
- `StrategyProfile.java:26-33` — toPromptSection() returns empty when guidelines empty
- `CognitionCore.java:33-372` — orchestrator composition, recordInteraction, tick dispatch
- `CognitionStack.java:59-513` — test-scope orchestrator wiring with Stage enum
- `ConversationRunner.java:22-208` — conversation loop driving the showcase
- `GoalProposalConfig.java` — 60-minute cooldown default
- `CuriosityDrive.java` — requires MemoryHygieneOrchestrator
- `EngagementSignal.java:9-36` — TurnOutcome + ConversationOutcome sealed hierarchy
- Issue #282 spec — CognitiveImpact and 6-arg recordInteraction
- Issue #258 spec — historical encounter showcase architecture
- Issue #261 spec — measurement-driven cognition stages
- Issue #283 — directive-minimal architecture (follow-on)
