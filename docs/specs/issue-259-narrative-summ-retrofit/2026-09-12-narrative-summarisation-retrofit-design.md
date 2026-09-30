# Narrative Summarisation Retrofit — Design Spec

**Issue:** casehubio/blocks#259
**Date:** 2026-09-12
**Branch:** issue-259-narrative-summ-retrofit
**Depends on:** #241 (decision narratives — landed)

## Overview

Two-part change: (A) introduce three new summarisation pipeline SPIs
deferred from #241, and (B) rewrite `NarrativeSynthesiser` as a
`ContentSummariser<ReflectionEntry, NarrativeState>` wired through
`SummarisationRunner`.

Part A enriches the pipeline with opt-in persistent state, pluggable
emission gating, and post-summarisation output processing. Part B
validates these SPIs against their first real consumer — the identity
narrative synthesis flow.

### What doesn't change

- `NarrativeFragment` sealed hierarchy, `NarrativeState`, `NarrativeScope`
- `NarrativeOrchestrator` — still reads from `NarrativeStore`, still a
  compositor, still exposes `currentNarrative()`
- `NarrativePromptSection`, `SocialAvatarCognition` — unchanged consumers
- `GroupNarrativeOrchestrator`
- All existing test behaviour (existing tests must pass unchanged)

## Part A — Pipeline SPIs

All three SPIs live in `summarisation-api`. They are opt-in extensions
to `SummarisationRunner` and `KeyedSummarisationRunner`, accessed via
a new builder API (D3).

### SPI 1: StateStore\<S\>

Opt-in persistent state for `SummarisationRunner`. When absent, the
existing in-memory `ConcurrentHashMap` behaviour is unchanged. When
present, write-through cache: reads check cache first, fall back to
`StateStore.load()`, always write to both cache and store.

```java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;

public interface StateStore<S> {
    @Nullable S load(String partitionKey);
    void store(String partitionKey, S state);
}
```

**Partition key semantics:**
- `SummarisationRunner`: uses `tenancyId` (or `"__default__"` when null),
  matching existing `partitionState` key semantics
- `KeyedSummarisationRunner`: uses `key.toString()`, converting the
  generic group key to a string for the persistence SPI

**Write-through cache in the runner:**

```java
// In invokeSummariser(), after summarise() completes:
if (result.newState() != null) {
    partitionState.put(partitionKey, result.newState());
    if (stateStore != null) {
        stateStore.store(partitionKey, result.newState());
    }
}

// On first read (cache miss):
Object prevState = partitionState.get(partitionKey);
if (prevState == null && stateStore != null) {
    prevState = stateStore.load(partitionKey);
    if (prevState != null) {
        partitionState.put(partitionKey, prevState);
    }
}
```

### SPI 2: EmissionPolicy\<IN, S\>

Replaces `WindowPolicy` as the gating mechanism for when buffered events
should be drained and summarised. The policy receives the buffered events,
the current summariser state, and the current time — enabling state-aware
emission decisions (e.g., novelty checks that compare new input against
existing state).

```java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;
import java.util.List;

@FunctionalInterface
public interface EmissionPolicy<IN, S> {
    boolean shouldEmit(List<LevelEvent<IN>> buffered,
                       @Nullable S currentState,
                       long now);

    static <IN, S> EmissionPolicy<IN, S> anyOf(
            List<EmissionPolicy<IN, S>> policies) {
        return (buffered, state, now) ->
                policies.stream().anyMatch(p -> p.shouldEmit(buffered, state, now));
    }

    static <IN, S> EmissionPolicy<IN, S> allOf(
            List<EmissionPolicy<IN, S>> policies) {
        return (buffered, state, now) ->
                policies.stream().allMatch(p -> p.shouldEmit(buffered, state, now));
    }
}
```

**WindowPolicy backward compatibility (D6):**

`WindowPolicy` does not implement `EmissionPolicy` directly (type
parameter mismatch — `WindowPolicy` is unparameterized). Instead, the
builder internally wraps `WindowPolicy` via a package-private adapter:

```java
class WindowPolicyEmission<IN, S> implements EmissionPolicy<IN, S> {
    private final WindowPolicy policy;

    WindowPolicyEmission(WindowPolicy policy) {
        this.policy = policy;
    }

    @Override
    public boolean shouldEmit(List<LevelEvent<IN>> buffered,
                              @Nullable S currentState, long now) {
        if (buffered.isEmpty()) return false;
        if (policy.maxCount() > 0 && buffered.size() >= policy.maxCount())
            return true;
        if (policy.maxAge() > 0) {
            long oldest = buffered.get(0).timestamp();
            return (now - oldest) >= policy.maxAge();
        }
        return false;
    }
}
```

**Runner-level evaluation (D2):**

When `EmissionPolicy` is set (via builder), `SummarisationRunner.tick()`
evaluates it directly instead of delegating to `EventAccumulator.drainIfReady()`:

```java
// In tick():
if (emissionPolicy != null) {
    var buffered = accumulator.peekBuffer();
    String partitionKey = resolvePartitionKey(buffered);
    Object state = resolveState(partitionKey);
    if (!emissionPolicy.shouldEmit(buffered, state, now)) {
        return CompletableFuture.completedFuture(null);
    }
    var batch = accumulator.drain();
    // ... compact and invoke summariser
}
```

`EventAccumulator` gains a single new method:

```java
public synchronized List<LevelEvent<E>> peekBuffer() {
    return List.copyOf(buffer);
}
```

Existing `shouldEmit()` and `drainIfReady()` remain for backward
compatibility with legacy constructors.

### SPI 3: OutputProcessor\<OUT, S\>

Post-summarisation output processing. Called after `summarise()` returns,
before publishing to the output bus. Receives the summariser outputs and
the current state. Returns a (possibly modified) list of outputs.

```java
package io.casehub.blocks.summarisation;

import org.jspecify.annotations.Nullable;
import java.util.List;

@FunctionalInterface
public interface OutputProcessor<OUT, S> {
    List<OUT> process(List<OUT> outputs, @Nullable S currentState);
}
```

**Integration point in invokeSummariser():**

```java
// After summarise() returns outputs (both stateful and stateless paths):
if (outputProcessor != null) {
    outputs = outputProcessor.process(outputs, currentState);
}
for (var payload : outputs) {
    outputBus.publish(new LevelEvent<>(payload, now, outputLevel, tenancyId));
}
```

### Builder API (D3)

New inner `Builder` class on both `SummarisationRunner` and
`KeyedSummarisationRunner`. Existing constructors unchanged.

```java
public class SummarisationRunner<IN, OUT> implements Tickable {

    // ... existing constructors unchanged ...

    public static <IN, OUT> Builder<IN, OUT> builder(
            Summariser<IN, OUT> summariser,
            EventStreamBus<OUT> outputBus,
            EventLevel outputLevel) {
        return new Builder<>(summariser, outputBus, outputLevel);
    }

    public static class Builder<IN, OUT> {
        private final Summariser<IN, OUT> summariser;
        private final EventStreamBus<OUT> outputBus;
        private final EventLevel outputLevel;
        private WindowPolicy windowPolicy;
        private EmissionPolicy<IN, ?> emissionPolicy;
        private StateStore<?> stateStore;
        private Function<List<LevelEvent<IN>>, String> stateKeyResolver;
        private OutputProcessor<OUT, ?> outputProcessor;
        private Compactor<IN> compactor;
        private Consumer<List<LevelEvent<IN>>> onFailure;

        Builder(Summariser<IN, OUT> summariser,
                EventStreamBus<OUT> outputBus,
                EventLevel outputLevel) {
            this.summariser = summariser;
            this.outputBus = outputBus;
            this.outputLevel = outputLevel;
        }

        public Builder<IN, OUT> windowPolicy(WindowPolicy policy) {
            this.windowPolicy = policy;
            return this;
        }

        public <S> Builder<IN, OUT> emissionPolicy(
                EmissionPolicy<IN, S> policy) {
            this.emissionPolicy = policy;
            return this;
        }

        public <S> Builder<IN, OUT> stateStore(StateStore<S> store) {
            this.stateStore = store;
            return this;
        }

        public Builder<IN, OUT> stateKeyResolver(
                Function<List<LevelEvent<IN>>, String> resolver) {
            this.stateKeyResolver = resolver;
            return this;
        }

        public <S> Builder<IN, OUT> outputProcessor(
                OutputProcessor<OUT, S> processor) {
            this.outputProcessor = processor;
            return this;
        }

        public Builder<IN, OUT> compactor(Compactor<IN> compactor) {
            this.compactor = compactor;
            return this;
        }

        public Builder<IN, OUT> onFailure(
                Consumer<List<LevelEvent<IN>>> onFailure) {
            this.onFailure = onFailure;
            return this;
        }

        public SummarisationRunner<IN, OUT> build() {
            // Validate: exactly one of windowPolicy or emissionPolicy set
            // If windowPolicy set, wrap as EmissionPolicy
            // Construct runner with all components
        }
    }
}
```

`KeyedSummarisationRunner` gets an analogous builder with the additional
required parameters (`keyExtractor`, `completionTest`, `staleTimeout`).
When `EmissionPolicy` is set on the keyed runner, it replaces the
`completionTest` predicate — the policy subsumes completion detection.

**Validation:** `build()` requires exactly one of `windowPolicy` or
`emissionPolicy`. If neither is set, throw `IllegalStateException`.
If both are set, throw `IllegalStateException`. `windowPolicy()`
internally wraps as `EmissionPolicy` via `WindowPolicyEmission`.

## Part B — Identity Narrative Retrofit

### Component split (D5)

The current `NarrativeSynthesiser` is decomposed into four pipeline
components:

| Current responsibility | New owner |
|---|---|
| Gate logic (count + novelty + quiet period) | `NarrativeEmissionPolicy` |
| Pull reflections from `ReflectionQueryStore` | `ReflectionEventAdapter` |
| LLM prompt assembly + invocation + parse | `NarrativeContentSummariser` (ContentSummariser impl) |
| Episode/theme building + merge | `NarrativeContentSummariser` |
| Episode/theme pruning | `NarrativeOutputProcessor` |
| State persistence (NarrativeStore) | `CbrStateStore` adapter + runner write-through |
| Per-agent locking | Runner's `synchronized tick()` |

### ReflectionEventAdapter (D1)

Pull-to-push adapter in the narrative package. Tick-driven: called from
the pipeline factory's tick cycle, queries `ReflectionQueryStore` for
new reflections, publishes each as `LevelEvent<ReflectionEntry>`.

```java
package io.casehub.blocks.agentic.social.narrative;

public class ReflectionEventAdapter {

    private final ReflectionQueryStore reflectionQueryStore;
    private final EventStreamBus<ReflectionEntry> outputBus;
    private final EventLevel outputLevel;
    private final ConcurrentHashMap<String, Instant> watermarks =
            new ConcurrentHashMap<>();

    public ReflectionEventAdapter(
            ReflectionQueryStore reflectionQueryStore,
            EventStreamBus<ReflectionEntry> outputBus,
            EventLevel outputLevel) {
        this.reflectionQueryStore = reflectionQueryStore;
        this.outputBus = outputBus;
        this.outputLevel = outputLevel;
    }

    public void publishNewReflections(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var since = watermarks.getOrDefault(key, Instant.EPOCH);
        var reflections = reflectionQueryStore.findSince(
                agentId, tenantId, since);
        if (reflections.isEmpty()) return;

        for (var r : reflections) {
            outputBus.publish(new LevelEvent<>(
                    r, r.generatedAt().toEpochMilli(),
                    outputLevel, tenantId));
        }

        var latest = reflections.getLast().generatedAt();
        watermarks.put(key, latest);
    }
}
```

**Watermark semantics:** The adapter tracks the `generatedAt` timestamp
of the last published reflection per agent+tenant. On each tick, it
queries `findSince(watermark)` and advances. First call uses
`Instant.EPOCH` — picks up all existing reflections. This matches the
existing `NarrativeSynthesiser` behaviour where `since` starts from
`currentState.synthesisedAt()` (or `EPOCH` for first synthesis).

### NarrativeEmissionPolicy

Implements `EmissionPolicy<ReflectionEntry, NarrativeState>`. Maps the
three `NarrativeSynthesisGate` checks to the emission policy contract.

```java
package io.casehub.blocks.agentic.social.narrative;

public class NarrativeEmissionPolicy
        implements EmissionPolicy<ReflectionEntry, NarrativeState> {

    private final NarrativeSynthesisGate gate;

    public NarrativeEmissionPolicy(NarrativeSynthesisGate gate) {
        this.gate = gate;
    }

    @Override
    public boolean shouldEmit(List<LevelEvent<ReflectionEntry>> buffered,
                              @Nullable NarrativeState currentState,
                              long now) {
        if (buffered.isEmpty()) return false;

        // Quiet period bypass — check first, overrides count + novelty
        if (currentState != null) {
            long sinceSynthesis = now - currentState.synthesisedAt()
                    .toEpochMilli();
            if (sinceSynthesis >= gate.quietPeriodBypass().toMillis()) {
                return true;
            }
        } else {
            // First synthesis — no existing state, always emit
            return true;
        }

        // Count gate
        if (buffered.size() < gate.minNewReflections()) {
            return false;
        }

        // Novelty gate
        var reflectionText = buffered.stream()
                .map(e -> e.payload().insight())
                .collect(Collectors.joining("\n"));
        var narrativeText = currentState.episodes().stream()
                .map(IndividualEpisode::description)
                .collect(Collectors.joining("\n"));
        double novelty = TokenJaccardDistance.distance(
                reflectionText, narrativeText);
        return novelty >= gate.noveltyThreshold();
    }
}
```

### NarrativeContentSummariser

The thin `ContentSummariser<ReflectionEntry, NarrativeState>` — keeps
only the LLM synthesis logic from the current `NarrativeSynthesiser`.
`@ApplicationScoped` for CDI-injected `AgentProvider`.

```java
package io.casehub.blocks.agentic.social.narrative;

@ApplicationScoped
public class NarrativeContentSummariser
        implements ContentSummariser<ReflectionEntry, NarrativeState> {

    static final String SYSTEM_PROMPT = /* same as current */;

    private final AgentProvider agentProvider;
    private final NarrativeConfig config;

    @Inject
    public NarrativeContentSummariser(AgentProvider agentProvider,
                                       NarrativeConfig config) {
        this.agentProvider = agentProvider;
        this.config = config;
    }

    @Override
    public CompletionStage<NarrativeState> summarise(
            List<ReflectionEntry> items,
            @Nullable NarrativeState previous) {
        // 1. Cap reflections at maxReflectionsPerSynthesis
        // 2. Assemble user prompt (existing assembleUserPrompt logic)
        // 3. Invoke LLM (existing agentProvider pattern)
        // 4. Parse JSON response (existing parseResponse logic)
        // 5. Build episodes from parsed specs + reflections
        // 6. Merge: existing episodes from previous + new episodes
        // 7. Re-derive themes from LLM output
        // 8. Return new NarrativeState (WITHOUT pruning — OutputProcessor
        //    handles that)
    }
}
```

**Key differences from current NarrativeSynthesiser:**
- No gate logic (moved to `NarrativeEmissionPolicy`)
- No pruning (moved to `NarrativeOutputProcessor`)
- No `ReflectionQueryStore` interaction (moved to `ReflectionEventAdapter`)
- No `NarrativeStore` writes (moved to `StateStore` / runner write-through)
- No per-agent locking (runner's `tick()` is synchronized)
- Receives `List<ReflectionEntry>` directly (unwrapped from `LevelEvent`
  by `ContentSummariser.asSummariser()` bridge)
- Returns `NarrativeState` as both output and new state (via
  `asSummariser()` bridge → `SummariseResult<NarrativeState, NarrativeState>`)

**Error handling:** LLM failure and parse failure return a completed
future with the previous state unchanged (or null for first synthesis).
The `asSummariser()` bridge wraps this as
`SummariseResult(List.of(previous), previous)` — the runner publishes
`previous` to the output bus and stores the same state. This is a
benign no-op: the state write is idempotent, and `NarrativeOrchestrator`
already deduplicates by comparing `synthesisedAt` timestamps (returns
`NoChange` when unchanged). No special handling needed.

### NarrativeOutputProcessor

Implements `OutputProcessor<NarrativeState, NarrativeState>`. Applies
episode and theme pruning — the same logic currently in
`NarrativeSynthesiser.pruneEpisodes()` and `pruneThemes()`.

```java
package io.casehub.blocks.agentic.social.narrative;

public class NarrativeOutputProcessor
        implements OutputProcessor<NarrativeState, NarrativeState> {

    private final NarrativeConfig config;

    public NarrativeOutputProcessor(NarrativeConfig config) {
        this.config = config;
    }

    @Override
    public List<NarrativeState> process(
            List<NarrativeState> outputs,
            @Nullable NarrativeState currentState) {
        return outputs.stream()
                .map(this::prune)
                .toList();
    }

    private NarrativeState prune(NarrativeState state) {
        var episodes = new ArrayList<>(state.episodes());
        pruneEpisodes(episodes);

        var themes = new ArrayList<>(state.themes());
        pruneThemes(themes);

        var allFragments = new ArrayList<NarrativeFragment>();
        allFragments.addAll(episodes);
        allFragments.addAll(state.groupEpisodes());
        allFragments.addAll(themes);

        return new NarrativeState(state.scopeId(), state.tenantId(),
                state.scope(), allFragments, state.synthesisedAt(),
                state.reflectionCountAtSynthesis());
    }

    // pruneEpisodes and pruneThemes — same logic as current
    // NarrativeSynthesiser, extracted verbatim
}
```

### CbrStateStore

Adapter wrapping `NarrativeMemory` as `StateStore<NarrativeState>`.

```java
package io.casehub.blocks.agentic.social.narrative;

public class CbrStateStore implements StateStore<NarrativeState> {

    private final CbrNarrativeStore delegate;

    public CbrStateStore(CbrNarrativeStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable NarrativeState load(String partitionKey) {
        // partitionKey is "agentId:tenantId"
        var parts = partitionKey.split(":", 2);
        return delegate.load(parts[0], parts[1]);
    }

    @Override
    public void store(String partitionKey, NarrativeState state) {
        delegate.store(state);
    }
}
```

**Note:** `CbrNarrativeStore.store(NarrativeState)` ignores the partition
key — the state carries its own `scopeId` and `tenantId`. The adapter
only uses the partition key for `load()` decomposition. This is safe
because `SummarisationRunner` derives the partition key from `tenancyId`
in the event, and the `ReflectionEventAdapter` publishes events with
`tenantId` as the `LevelEvent.tenancyId()`.

**Partition key for narrative pipeline:** The `SummarisationRunner` uses
`tenancyId` as the partition key by default. But narrative state is
per-agent, not per-tenant. The runner needs a composite key. Since the
runner partitions by `tenancyId` from the event, and the adapter
publishes with `tenantId` only, we need to encode `agentId` into the
partition key.

Two options:
1. Use `agentId:tenantId` as tenancyId in the LevelEvent — overloads
   the tenancy concept
2. Use the builder's `EmissionPolicy` path where the runner resolves
   state via a custom key

Option 2 is cleaner. The runner's emission policy path already receives
the buffered events — it can derive `agentId` from the first event's
payload. The `StateStore` partition key becomes `agentId + ":" + tenantId`
constructed by the runner (or by a key resolver function provided to the
builder).

**Revised approach:** Add a `stateKeyResolver` to the builder that
extracts the partition key from a batch. Default: first event's
`tenancyId()`. Narrative pipeline overrides with
`batch -> batch.get(0).payload().agentId() + ":" + batch.get(0).tenancyId()`.

```java
// Builder addition:
public Builder<IN, OUT> stateKeyResolver(
        Function<List<LevelEvent<IN>>, String> resolver) {
    this.stateKeyResolver = resolver;
    return this;
}
```

### NarrativePipeline (D7)

`@ApplicationScoped` factory wiring all components. Mirrors
`DecisionNarrativePipeline`.

```java
package io.casehub.blocks.agentic.social.narrative;

@ApplicationScoped
public class NarrativePipeline {

    static final EventLevel REFLECTIONS =
            new EventLevel("reflections", 0);
    static final EventLevel NARRATIVES =
            new EventLevel("narratives", 1);

    private final EventStreamBus<ReflectionEntry> reflectionBus;
    private final EventStreamBus<NarrativeState> narrativeBus;
    private final SummarisationRunner<ReflectionEntry, NarrativeState>
            runner;
    private final ReflectionEventAdapter adapter;

    @Inject
    public NarrativePipeline(
            NarrativeContentSummariser summariser,
            NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore,
            CbrNarrativeStore cbrStore) {

        this.reflectionBus = new EventStreamBus<>();
        this.narrativeBus = new EventStreamBus<>();

        this.adapter = new ReflectionEventAdapter(
                reflectionQueryStore, reflectionBus, REFLECTIONS);

        this.runner = SummarisationRunner
                .<ReflectionEntry, NarrativeState>builder(
                        summariser.asSummariser(),
                        narrativeBus, NARRATIVES)
                .emissionPolicy(
                        new NarrativeEmissionPolicy(config.synthesisGate()))
                .stateStore(new CbrStateStore(cbrStore))
                .stateKeyResolver(batch ->
                        batch.get(0).payload().agentId() + ":"
                        + batch.get(0).tenancyId())
                .outputProcessor(
                        new NarrativeOutputProcessor(config))
                .build();

        reflectionBus.subscribe(e -> true, runner::collect);
    }

    public void tick(String agentId, String tenantId) {
        adapter.publishNewReflections(agentId, tenantId);
        runner.tick(System.currentTimeMillis());
    }

    public EventStreamBus<NarrativeState> narrativeBus() {
        return narrativeBus;
    }
}
```

**Integration with InnerLifeOrchestrator:** The `InnerLifeOrchestrator`
does not currently call `NarrativeSynthesiser` (confirmed: zero
production references). `NarrativeSynthesiser` is `@ApplicationScoped`
but not injected. The pipeline is self-contained — it is either:
- Tick-driven by a `PipelineTickScheduler` (periodic, like
  `DecisionNarrativePipeline`)
- Called explicitly by whatever scheduler currently drives narrative
  synthesis in the downstream consumer

The pipeline exposes `tick(agentId, tenantId)` for explicit invocation,
matching the current `synthesiseIfNeeded(agentId, tenantId)` call
pattern.

### NarrativeOrchestrator — unchanged

`NarrativeOrchestrator` continues to read from `NarrativeStore` on
its own tick cycle. The `NarrativePipeline`'s `StateStore` write-through
writes to `NarrativeMemory`, which is the same `NarrativeStore` that
`NarrativeOrchestrator` reads. No coupling between the two — the
orchestrator discovers new synthesis via timestamp comparison, same as
before.

## Package structure

### New files in summarisation-api

```
summarisation-api/src/main/java/io/casehub/blocks/summarisation/
├── EmissionPolicy.java           (new — @FunctionalInterface SPI)
├── StateStore.java               (new — interface SPI)
├── OutputProcessor.java          (new — @FunctionalInterface SPI)
├── WindowPolicyEmission.java     (new — package-private adapter)
├── EventAccumulator.java         (modified — add peekBuffer())
├── SummarisationRunner.java      (modified — add Builder, EmissionPolicy
│                                   evaluation path)
└── KeyedSummarisationRunner.java (modified — add Builder)
```

### New files in blocks

```
blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/
├── NarrativeContentSummariser.java  (new — ContentSummariser impl)
├── NarrativeEmissionPolicy.java     (new — EmissionPolicy impl)
├── NarrativeOutputProcessor.java    (new — OutputProcessor impl)
├── CbrStateStore.java               (new — StateStore adapter)
├── ReflectionEventAdapter.java      (new — pull-to-push bridge)
├── NarrativePipeline.java           (new — @ApplicationScoped factory)
└── NarrativeSynthesiser.java        (DELETED — replaced by above)
```

### Modified files

| File | Change |
|------|--------|
| `EventAccumulator.java` | Add `peekBuffer()` |
| `SummarisationRunner.java` | Add `Builder`, emission policy evaluation path in `tick()`, state store integration in `invokeSummariser()`, output processor call |
| `KeyedSummarisationRunner.java` | Add `Builder` (same pattern), state store + output processor integration |

## Migration path

### NarrativeSynthesiser deletion

`NarrativeSynthesiser` has **zero production callers** within blocks.
It is `@ApplicationScoped` but not injected by any class in this repo.
`SocialAvatarCognition` uses `NarrativeOrchestrator` (the compositor),
not the synthesiser. External consumers that depend on
`NarrativeSynthesiser` (if any) would need to switch to
`NarrativePipeline.tick()` — the call signature is identical
(`agentId, tenantId`).

### NarrativeSynthesisGate preservation

`NarrativeSynthesisGate` remains as a config record — it is still used
by `NarrativeConfig` and consumed by `NarrativeEmissionPolicy`. No
change needed.

## Test plan

### Part A — Pipeline SPI tests

| Test class | What it verifies |
|---|---|
| `EmissionPolicyTest` | `anyOf()` / `allOf()` composition, edge cases (empty buffer, null state) |
| `StateStoreIntegrationTest` | Write-through cache: load falls back to store, writes go to both. Eviction. |
| `OutputProcessorTest` | Process called after summarise, before output bus publish. Empty list passthrough. |
| `SummarisationRunnerBuilderTest` | Builder validation (one of windowPolicy/emissionPolicy required), all optional params |
| `SummarisationRunnerEmissionPolicyTest` | Emission policy evaluation in tick(), peekBuffer + drain flow |
| `KeyedSummarisationRunnerBuilderTest` | Builder validation, state store with keyed state |
| Existing `SummarisationRunnerTest` | Must pass unchanged — backward compat |
| Existing `KeyedSummarisationRunnerTest` | Must pass unchanged |
| Existing `KeyedSummarisationRunnerStatefulTest` | Must pass unchanged |

### Part B — Narrative retrofit tests

| Test class | What it verifies |
|---|---|
| `NarrativeEmissionPolicyTest` | Count gate, novelty gate, quiet period bypass, first synthesis (null state) |
| `NarrativeContentSummariserTest` | LLM prompt assembly, parse, episode/theme building, merge, error handling. Mirrors existing `NarrativeSynthesiserTest` assertions. |
| `NarrativeOutputProcessorTest` | Episode pruning (drop oldest beyond max), theme pruning (drop lowest salience, salience floor) |
| `CbrStateStoreTest` | Partition key parsing, load/store delegation |
| `ReflectionEventAdapterTest` | Watermark tracking, empty query, publishes with correct tenancyId |
| `NarrativePipelineTest` | End-to-end: adapter → bus → runner → emission gate → summarise → prune → output bus |
| Existing `NarrativeOrchestratorTest` | Must pass unchanged |
| Existing `NarrativeSynthesiserTest` | **Deleted** — replaced by `NarrativeContentSummariserTest` and `NarrativeEmissionPolicyTest` (test coverage must be equivalent) |

## References

- `SummarisationRunner.java` — current runner implementation (lines 112-132: invokeSummariser)
- `KeyedSummarisationRunner.java` — keyed runner with evictState (line 167)
- `EventAccumulator.java` — current buffer with WindowPolicy-based shouldEmit
- `WindowPolicy.java` — current gating record
- `ContentSummariser.java` — asSummariser() bridge (line 13)
- `StatefulSummariser.java` — SummariseResult (line 18)
- `NarrativeSynthesiser.java` — current implementation being refactored
- `NarrativeOrchestrator.java` — compositor (unchanged)
- `NarrativeSynthesisGate.java` — gate config (preserved)
- `CbrNarrativeStore.java` — CBR persistence (wrapped by CbrStateStore)
- `ReflectionQueryStore.java` — reflection read SPI (consumed by adapter)
- `DecisionNarrativePipeline.java` — factory pattern precedent (#241)
- `ChannelEventAdapter.java` — adapter pattern precedent
- Spec: `issue-241-decision-narratives/2026-09-11-decision-narratives-design.md` — deferred SPIs (D1-D3)
- Spec: `issue-142-narrative-identity/2026-08-24-narrative-orchestrator-synthesiser-design.md` — original NarrativeSynthesiser design
- decisions.md — D1-D7 architectural decisions
