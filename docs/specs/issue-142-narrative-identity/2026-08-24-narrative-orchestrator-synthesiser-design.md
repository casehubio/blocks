# NarrativeOrchestrator + NarrativeSynthesiser — Implementation Spec

**Issue:** casehubio/blocks#143
**Date:** 2026-08-24
**Branch:** issue-142-narrative-identity
**Parent spec:** 2026-08-24-narrative-identity-social-emergence-design.md (#142)

## Overview

Implements the two-component split architecture (D3) for narrative identity:

1. **NarrativeSynthesiser** — effectful `@ApplicationScoped` bean (D17). Reads
   reflections via `ReflectionQueryStore`, evaluates the composite synthesis gate
   (D6), calls `AgentProvider` for LLM synthesis, merges results incrementally
   (D13/D14), prunes by capacity (D18), and writes to `NarrativeStore`.

2. **NarrativeOrchestrator** — compositor `@ApplicationScoped` bean. Reads from
   `NarrativeStore` on `tick()`, caches `NarrativeState`, exposes
   `currentNarrative()` accessor. No LLM, no side effects. Gracefully degrades
   on synthesis failure — stale state is valid (D21).

NarrativeSynthesiser follows the DriveOrchestrator pattern: `ConcurrentHashMap`
cache, per-agent `ReentrantLock`, CDI constructor + test constructor with
`Clock`. NarrativeOrchestrator is a simpler compositor: `ConcurrentHashMap`
cache, per-agent `ReentrantLock`, single CDI constructor (no Clock needed —
reads timestamps from persisted state).

### Upstream change

`TokenJaccardDistance` in `io.casehub.blocks.agentic.social` — both the
class AND the `distance()` method changed from package-private to `public`
(D16). NarrativeSynthesiser needs it for novelty scoring.

## NarrativeOrchestrator

Trivial compositor. Mirrors DriveOrchestrator's structure.

```java
@ApplicationScoped
public class NarrativeOrchestrator {

    private final NarrativeStore store;
    private final ConcurrentHashMap<String, NarrativeState> cache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks =
            new ConcurrentHashMap<>();

    @Inject
    public NarrativeOrchestrator(NarrativeStore store) {
        this.store = store;
    }

    public NarrativeTick tick(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            var loaded = store.load(agentId, tenantId);
            var previous = cache.get(key);

            if (loaded == null) {
                return new NarrativeTick.NoChange("no narrative in store");
            }

            cache.put(key, loaded);

            if (previous == null) {
                return new NarrativeTick.Updated(loaded, loaded,
                        loaded.episodes().stream().map(IndividualEpisode::id).toList(),
                        loaded.themes().stream().map(DerivedTheme::label).toList());
            }

            if (loaded.synthesisedAt().equals(previous.synthesisedAt())) {
                return new NarrativeTick.NoChange("no new synthesis");
            }

            var newEpisodeIds = loaded.episodes().stream()
                    .map(IndividualEpisode::id)
                    .filter(id -> previous.episodes().stream()
                            .noneMatch(e -> e.id().equals(id)))
                    .toList();
            var newThemeLabels = loaded.themes().stream()
                    .map(DerivedTheme::label)
                    .filter(label -> previous.themes().stream()
                            .noneMatch(t -> t.label().equalsIgnoreCase(label)))
                    .toList();

            return new NarrativeTick.Updated(previous, loaded,
                    newEpisodeIds, newThemeLabels);
        } finally {
            lock.unlock();
        }
    }

    public Optional<NarrativeState> currentNarrative(
            String agentId, String tenantId) {
        return Optional.ofNullable(cache.get(agentId + ":" + tenantId));
    }
}
```

### Change detection

Comparison uses `synthesisedAt` timestamp equality. If the store returns a
`NarrativeState` with the same `synthesisedAt` as the cached state, no
synthesis has occurred — return `NoChange`. This avoids deep structural
comparison and is correct because the synthesiser sets a new timestamp on
every successful synthesis.

## NarrativeSynthesiser

### Class structure

```java
@ApplicationScoped
public class NarrativeSynthesiser {

    private final AgentProvider agentProvider;
    private final NarrativeStore narrativeStore;
    private final ReflectionQueryStore reflectionQueryStore;
    private final NarrativeConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, ReentrantLock> synthesisLocks =
            new ConcurrentHashMap<>();

    @Inject
    public NarrativeSynthesiser(AgentProvider agentProvider,
                                 NarrativeStore narrativeStore,
                                 ReflectionQueryStore reflectionQueryStore,
                                 NarrativeConfig config) {
        this(agentProvider, narrativeStore, reflectionQueryStore,
             config, Clock.systemUTC());
    }

    NarrativeSynthesiser(AgentProvider agentProvider,
                          NarrativeStore narrativeStore,
                          ReflectionQueryStore reflectionQueryStore,
                          NarrativeConfig config,
                          Clock clock) {
        this.agentProvider = agentProvider;
        this.narrativeStore = narrativeStore;
        this.reflectionQueryStore = reflectionQueryStore;
        this.config = config;
        this.clock = clock;
    }

    public NarrativeSynthesisTick synthesiseIfNeeded(
            String agentId, String tenantId) { ... }
}
```

### Synthesis flow

`synthesiseIfNeeded(agentId, tenantId)`:

Per-agent locking wraps the entire flow — `synthesisLocks.computeIfAbsent(key, k -> new ReentrantLock())`.
This prevents concurrent synthesis for the same agent (duplicate episodes,
wasted LLM calls, store write races).

1. **Load current state** — `narrativeStore.load(agentId, tenantId)`.
   Null = first synthesis (empty narrative).

2. **Determine since timestamp** — if current state exists, use
   `state.synthesisedAt()`. Otherwise `Instant.EPOCH`. (Using last
   *successful* synthesis, not last attempt — failed attempts don't
   reset the quiet period timer, ensuring retry after failures.)

3. **Count gate** — `reflectionQueryStore.countSince(agentId, tenantId, since)`.
   If count < `config.synthesisGate().minNewReflections()` AND quiet period
   hasn't elapsed → `Skipped("insufficient reflections: N")`.

4. **Quiet period check** — if time since last synthesis (or `Instant.EPOCH`)
   exceeds `config.synthesisGate().quietPeriodBypass()`, bypass count AND
   novelty gates. Still requires at least 1 reflection to avoid empty
   synthesis.

5. **Fetch reflections** — `reflectionQueryStore.findSince(agentId, tenantId, since)`,
   capped at `config.maxReflectionsPerSynthesis()`.

6. **Novelty gate** (if not quiet-period-bypassed) — compute
   `TokenJaccardDistance.distance(reflectionText, currentNarrativeText)`.
   If distance < `config.synthesisGate().noveltyThreshold()` →
   `Skipped("low novelty")`.

   **Text assembly:** `reflectionText` = all fetched reflection insights
   joined with newlines. `currentNarrativeText` = concatenation of all
   existing episode descriptions joined with newlines. If current state is
   null (first synthesis), `currentNarrativeText` is empty string —
   distance returns 1.0, always passing the novelty gate.

7. **Assemble prompt** — build system prompt + user prompt with:
   - Existing episode summaries (description + emotional valence)
   - Existing theme labels (for label stability per D15)
   - New reflections (numbered for index references)
   - JSON response schema instructions

8. **Invoke LLM** — `agentProvider.invoke(AgentSessionConfig.of(systemPrompt, userPrompt))`.
   Collect text deltas via blocking extraction (GE-20260801-0aee7e pattern).
   On failure → log warning, return `Skipped("llm failure")`.

9. **Parse response** — extract JSON from response text (strip markdown
   fences if present). Parse using `jakarta.json` (JSON-P) for consistency
   with InnerLifeOrchestrator — manual field extraction from `JsonObject`
   with per-field error handling. Parse into `SynthesisResult` record
   (newEpisodes, themes). On parse failure → log warning, return
   `Skipped("parse failure")`.

10. **Build fragments** — for each new episode: generate UUID for `id`,
    map `fromReflections` indices to the matched `ReflectionEntry` instances
    and flatten their `sourceCaseIds` into the episode's `sourceReflectionIds`
    field (transitive CBR case provenance — `ReflectionEntry` has no identity
    field, so the episode tracks which CBR cases contributed via the
    reflection path). Create `IndividualEpisode` with
    `from = Instant.now(clock)`.

    For themes: create `DerivedTheme` with label, salience, axisWeights.
    Compute `supportingFragmentIds` via thematic tag matching against all
    episodes (existing + newly created): an episode is a supporting fragment
    if it shares **at least one thematic tag** with the theme. Episodes or
    themes with empty `thematicTags` produce no matches. Fragment IDs are
    the episode `id()` values.

11. **Merge** — combine existing episodes (from current state) with new
    episodes. Themes are fully replaced by the LLM's output (D14).

12. **Prune** (D18) — if episodes > maxEpisodes, drop oldest by `from`.
    If themes > maxThemes, drop lowest salience. Drop any theme below
    `themeSalienceFloor`.

13. **Write** — `narrativeStore.store(new NarrativeState(agentId, tenantId,
    NarrativeScope.INDIVIDUAL, allFragments, Instant.now(clock),
    newReflections.size()))`.

    `reflectionCountAtSynthesis` = count of new reflections consumed in
    this synthesis batch (not cumulative). Matches the value returned in
    `NarrativeSynthesisTick.Synthesised.newReflectionsConsumed()`.

14. **Return** — `Synthesised(state, newReflections.size())`.

### LLM prompt design

**System prompt:**
```
You are synthesising a first-person narrative identity from an agent's
reflections and experiences. Given the agent's existing narrative context
and new reflections, produce:

1. New episodes — significant experiences from the reflections. Each
   episode has a description, emotional valence [-1.0, 1.0], and
   thematic tags.

2. Updated themes — identity themes derived from ALL episodes (existing
   and new). Each theme has a label, salience [0.0, 1.0], thematic tags,
   and per-axis drive modulation weights.

Drive axes: CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY.
Axis weights range [-1.0, 1.0]: positive amplifies the drive, negative
dampens it.

Respond with JSON only. No explanation outside the JSON.
```

**User prompt structure:**
```
## Existing Episodes
1. [description] (valence: X.X)
2. [description] (valence: X.X)
...

## Existing Theme Labels
- crisis-helper
- technical-expert
...

## New Reflections
0. [reflection insight text]
1. [reflection insight text]
...

## Response Format
{
  "newEpisodes": [
    {
      "description": "...",
      "emotionalValence": 0.0,
      "thematicTags": ["..."],
      "fromReflections": [0, 2]
    }
  ],
  "themes": [
    {
      "label": "...",
      "salience": 0.0,
      "thematicTags": ["..."],
      "axisWeights": {
        "CURIOSITY": 0.0,
        "COMPETENCE": 0.0,
        "AFFILIATION": 0.0,
        "AUTONOMY": 0.0
      }
    }
  ]
}
```

### JSON parsing

Parse into an internal `SynthesisResult` record:

```java
record SynthesisResult(
    List<EpisodeSpec> newEpisodes,
    List<ThemeSpec> themes) {}

record EpisodeSpec(
    String description,
    double emotionalValence,
    List<String> thematicTags,
    List<Integer> fromReflections) {}

record ThemeSpec(
    String label,
    double salience,
    List<String> thematicTags,
    Map<String, Double> axisWeights) {}
```

These are internal parsing types — not part of the public API. The
synthesiser converts them to `IndividualEpisode` and `DerivedTheme`
instances with proper IDs, timestamps, and validated fields. Invalid
values (out-of-range valence, unknown axis names) are logged and
skipped rather than failing the entire synthesis.

### NarrativeModulation clamping (D19)

`NarrativeModulation.compute()` must be updated to clamp the per-axis
result to [-1, 1] after summing across themes:

```java
public static Map<DriveAxis, Double> compute(NarrativeState narrative) {
    var modulation = new EnumMap<DriveAxis, Double>(DriveAxis.class);
    for (var theme : narrative.themes()) {
        for (var entry : theme.axisModulationWeights().entrySet()) {
            modulation.merge(entry.getKey(),
                    theme.salience() * entry.getValue(), Double::sum);
        }
    }
    modulation.replaceAll((axis, value) -> Math.clamp(value, -1.0, 1.0));
    return Map.copyOf(modulation);
}
```

### Error handling

- **LLM invocation failure**: catch all exceptions, log warning, return
  `Skipped("llm failure")`. Compositor sees stale state (D21).
- **JSON parse failure**: log warning with response text, return
  `Skipped("parse failure")`.
- **Individual field validation failure**: skip the invalid episode/theme,
  log warning, continue with remaining valid items. A synthesis that
  produces some valid items and some invalid ones still writes the valid
  items.
- **Empty LLM response**: return `Skipped("empty response")`.
- **Store write failure**: propagate exception — the consumer's scheduler
  handles it.

## Package Structure

```
blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/
├── (existing foundation types — NarrativeFragment, NarrativeState, etc.)
├── NarrativeOrchestrator.java     (NEW — compositor)
└── NarrativeSynthesiser.java      (NEW — effectful LLM synthesis)

blocks/src/main/java/io/casehub/blocks/agentic/social/
└── TokenJaccardDistance.java      (MODIFIED — package-private → public)

blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/
└── NarrativeModulation.java       (MODIFIED — add per-axis clamping)
```

## Testing Strategy

### NarrativeOrchestrator tests (Mockito)

| Test | What it verifies |
|------|-----------------|
| `tick_firstLoad_returnsUpdated` | Store returns state, no cache → Updated with all episode IDs and theme labels |
| `tick_noStateInStore_returnsNoChange` | Store returns null → NoChange("no narrative in store") |
| `tick_sameTimestamp_returnsNoChange` | Store returns same synthesisedAt → NoChange("no new synthesis") |
| `tick_newSynthesis_detectsNewEpisodesAndThemes` | Store returns newer state → Updated with correct new IDs/labels |
| `currentNarrative_returnsEmpty_beforeTick` | No tick → Optional.empty() |
| `currentNarrative_returnsCached_afterTick` | After tick → cached state returned |
| `tick_perAgentIsolation` | Different agent keys maintain independent caches |
| `tick_threadSafety` | Concurrent ticks for different agents don't interfere |

### NarrativeSynthesiser tests (Mockito)

| Test | What it verifies |
|------|-----------------|
| `synthesise_insufficientReflections_skipped` | Count below minNewReflections → Skipped |
| `synthesise_lowNovelty_skipped` | Novelty below threshold → Skipped |
| `synthesise_quietPeriodBypass` | Elapsed time > quietPeriodBypass → bypasses count and novelty |
| `synthesise_quietPeriodBypass_requiresAtLeastOneReflection` | Quiet period but zero reflections → Skipped |
| `synthesise_firstSynthesis_noExistingState` | No current state → synthesises from scratch |
| `synthesise_incrementalMerge_preservesExistingEpisodes` | Existing episodes retained + new episodes added |
| `synthesise_themesFullyReDerived` | Themes from LLM replace all existing themes |
| `synthesise_episodePruning_dropsOldest` | Over maxEpisodes → oldest by `from` dropped |
| `synthesise_themePruning_dropsLowestSalience` | Over maxThemes → lowest salience dropped |
| `synthesise_themeSalienceFloor` | Themes below floor pruned regardless of count |
| `synthesise_llmFailure_skipped` | AgentProvider throws → Skipped("llm failure") |
| `synthesise_parseFailure_skipped` | Invalid JSON → Skipped("parse failure") |
| `synthesise_partiallyInvalidResponse_writesValidItems` | Mix of valid/invalid items → valid items written |
| `synthesise_reflectionIndexMapping` | fromReflections indices correctly map to sourceCaseIds |
| `synthesise_themeLabelsIncludedInPrompt` | Existing theme labels appear in user prompt |
| `synthesise_maxReflectionsCapped` | More reflections than maxReflectionsPerSynthesis → capped |
| `synthesise_writesCorrectNarrativeState` | Written state has correct scopeId, tenantId, scope, synthesisedAt |
| `synthesise_perAgentLocking` | Concurrent calls for same agent serialise — no duplicate episodes |

### NarrativeModulation clamping tests

| Test | What it verifies |
|------|-----------------|
| `compute_clampsPositiveOverflow` | Multiple themes with positive weights → clamped at 1.0 |
| `compute_clampsNegativeOverflow` | Multiple themes with negative weights → clamped at -1.0 |
| `compute_withinBounds_noClamping` | Single theme within bounds → no change |

### Test infrastructure

Use the `TestAgentProvider` inner class pattern (GE-20260801-bcff35) for
LLM testing — implements `AgentProvider` with canned responses. Verifies
prompt content via `lastUserPrompt` inspection.

For Mockito-based tests: mock `AgentProvider` per GE-20260701-5c818b
(not a functional interface — must use `Mockito.mock()`, not lambdas).

## References

- [spec D3] — split architecture (NarrativeSynthesiser + NarrativeOrchestrator)
- [spec D6] — composite synthesis gate
- [D13] — incremental merge strategy
- [D14] — hybrid: episodes additive, themes re-derived
- [D15] — flat JSON with index references, theme label anchoring
- [D16] — TokenJaccardDistance made public
- [D17] — @ApplicationScoped CDI pattern
- [D18] — pruning and theme identity
- [D19] — NarrativeModulation per-axis clamping
- [D20] — DerivedTheme typed DriveAxis coupling confirmed
- [D21] — compositor graceful degradation
- [DriveOrchestrator.java] — compositor pattern reference
- [InnerLifeOrchestrator.java] — LLM invocation + JSON parsing reference
- [GE-20260801-bcff35] — TestAgentProvider pattern
- [GE-20260801-0aee7e] — AgentEvent text extraction
- [GE-20260701-5c818b] — AgentProvider not a functional interface
- [GE-20260719-f5ccc9] — CDI ConcurrentHashMap gotcha
