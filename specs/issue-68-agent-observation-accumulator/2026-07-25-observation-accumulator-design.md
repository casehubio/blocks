# Observation Accumulator — Design Spec

**Date:** 2026-07-25
**Issue:** #68
**Status:** Draft
**Repo:** casehubio/blocks
**Package:** `io.casehub.blocks.summarisation.observation`

## Problem

LLM agents in multi-event systems need batched, tiered observations of "what
happened since you last acted." The current options are one prompt per event
(expensive, flooding) or state snapshot only (loses temporal information). An
agent needs a rendered summary at an appropriate level of detail based on how
much happened, plus structured chunks for RAG retrieval.

## Design Principle — Terminal Consumer

The observation accumulator is a **terminal consumer** of the summarisation
pipeline, not a pipeline stage. `SummarisationRunner` transforms events at one
level into events at the next and publishes them to an output bus.
`ObservationAccumulator` sits at the end — it produces human-readable text for
an LLM prompt and structured chunks for memory storage. No output bus. No next
level.

```
L1 bus → SummarisationRunner → L2 bus → SummarisationRunner → L3 bus
                                                                  ↓
                                            ObservationAccumulator → ObservationResult (for prompt)
```

An agent subscribes its accumulator at whatever level of the hierarchy is
appropriate. A low-level agent subscribes at L1 (raw events). A supervisory
agent subscribes at L3 (already-summarised phases). The pipeline does
hierarchical compression; the accumulator does final rendering.

This distinction drives three structural decisions:

1. **Own buffer, not `EventAccumulator`.** `EventAccumulator` carries
   `WindowPolicy` for tick-driven emission. Observation draining is
   demand-driven — the agent's turn determines when to drain, not a policy.
   The accumulator needs a synchronized buffer with atomic copy-and-clear but
   not the `shouldEmit()` logic.

2. **No `KeyedAccumulator` for per-agent scoping.** Per-agent scoping is via
   bus subscription filtering, not internal keying. One accumulator per agent,
   each subscribing with a visibility predicate:
   ```java
   bus.subscribe(e -> agent.canSee(e), accumulator::collect);
   ```

3. **Different from `Summariser<IN, OUT>`.** `Summariser` is many-to-many
   (`List<OUT>` output). Observation rendering is many-to-one
   (`ObservationResult` output). Same async shape (`CompletionStage`), same
   input shape (`List<LevelEvent<E>>`), different operation semantics.

## Types (7 source files)

### `ObservationTier`

```java
public record ObservationTier(String name, int ordinal) {}
```

Named tier in the rendering hierarchy — mirrors `EventLevel` (named level in
the summarisation hierarchy). Both are records for extensibility: custom
`ObservationRenderer` implementations can define their own tiers.

Predefined constants:
```java
public static final ObservationTier VERBATIM = new ObservationTier("verbatim", 0);
public static final ObservationTier GROUPED = new ObservationTier("grouped", 1);
public static final ObservationTier SUMMARISED = new ObservationTier("summarised", 2);
```

`EventLevel` is the vertical axis (which level of the pipeline). `ObservationTier`
is the horizontal axis (which compression at rendering time). Same shape,
orthogonal dimensions.

Ordinal semantics: higher ordinal = more compression. The predefined constants
follow this (VERBATIM=0 < GROUPED=1 < SUMMARISED=2). Ordinals are informational
— the `TieredObservationRenderer` selects tiers by batch-size thresholds, not by
ordinal comparison. Custom `ObservationRenderer` implementations define their own
tiers with whatever ordinals fit their scheme. No uniqueness constraint — consistent
with `EventLevel`, where custom levels may share ordinals.

### `ObservationContext`

```java
public record ObservationContext(long currentTime, long timeSinceLastDrain) {}
```

Render-time context passed to the renderer. Follows the `*Context` naming
pattern established by `ClassificationContext`, `ConvergenceContext`,
`RenderContext`.

- `currentTime` — for relative timestamp formatting ("2s ago")
- `timeSinceLastDrain` — for the observation header ("12 seconds since your
  last action")

### `ObservationChunk`

```java
public record ObservationChunk(
        String content,
        long timestamp,
        ObservationTier tier,
        int eventCount,
        Map<String, String> metadata) {}
```

Independently retrievable unit for RAG storage. Agent-agnostic — the consumer
adds identity (agent ID, room context, topic) when storing to their memory
system. The accumulator and renderer produce domain-neutral chunks; the
consumer maps to their storage model.

- `content` — rendered text for this chunk
- `timestamp` — for VERBATIM: the event's timestamp. For GROUPED/SUMMARISED:
  the drain time
- `tier` — which tier produced this chunk
- `eventCount` — source events this chunk represents (1 for verbatim, N for
  grouped/summarised)
- `metadata` — extensible. Renderer can add keys (e.g., group key for GROUPED
  chunks). Consumer adds storage-specific keys.

### `ObservationResult`

```java
public record ObservationResult(
        String renderedText,
        List<ObservationChunk> chunks,
        int eventCount,
        long timeSinceLastDrain,
        ObservationTier tier) {}
```

Single drain's output. Carries both the prompt text and the RAG chunks,
produced in one rendering pass.

- `renderedText` — formatted text for the agent's prompt (header + content)
- `chunks` — individual RAG-able units
- `tier` — which tier was selected for this drain (null for empty results)
- `eventCount` — total source events across all chunks
- `timeSinceLastDrain` — echoed from context for consumer convenience

Factory method:
- `ObservationResult.empty(long timeSinceLastDrain)` — returns a result with
  empty text, empty chunks, `eventCount = 0`, `tier = null`. Eliminates null
  return from `CompletionStage` (follows `RenderContext.EMPTY` pattern).

### `ObservationRenderer<E>`

```java
@FunctionalInterface
public interface ObservationRenderer<E> {
    CompletionStage<ObservationResult> render(
            List<LevelEvent<E>> events, ObservationContext context);
}
```

SPI for rendering accumulated events into an observation. Parallels
`Summariser<IN, OUT>` structurally (same input shape, same async shape) but
serves a different role (terminal rendering vs pipeline transformation).

Implementations are stateless and shareable across accumulator instances.

### `TieredObservationRenderer<E>`

```java
public class TieredObservationRenderer<E> implements ObservationRenderer<E> {

    // Two-tier: verbatim + grouped (no LLM needed)
    public TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold)

    // Three-tier: verbatim + grouped + summarised
    public TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold,
            int groupedThreshold,
            Summariser<E, String> summariser)

    // Header customisation (applies to any tier configuration)
    public TieredObservationRenderer<E> withHeaderFormatter(
            Function<ObservationContext, String> headerFormatter)
}
```

Standard tiered implementation. Routes based on batch size:

| Tier | Condition | Rendering | Chunks |
|------|-----------|-----------|--------|
| VERBATIM | size ≤ verbatimThreshold | Each event as a timestamped line | One per event |
| GROUPED | size ≤ groupedThreshold (or no summariser) | Events partitioned by group key, rendered under headers | One per group |
| SUMMARISED | size > groupedThreshold (requires summariser) | Full batch to `Summariser<E, String>`, result list joined with newlines | One total |

Constructor parameters:

- `eventRenderer` — `Function<E, String>`: renders a single event payload as
  description text. Used by both verbatim (full line) and grouped (item within
  group) tiers. The tier implementation adds timestamps and structural framing.
- `groupKeyExtractor` — `Function<E, String>`: extracts the group key for the
  grouped tier (e.g., "Dialogue", "Movement", "Interactions").
- `verbatimThreshold` — batch sizes at or below this use verbatim rendering.
  Must be `>= 0`. Zero means "never use verbatim" — all batches go to grouped
  (or summarised).
- `groupedThreshold` — batch sizes above verbatimThreshold and at or below this
  use grouped rendering. Only meaningful when a summariser is provided. Must be
  `> verbatimThreshold` — otherwise the GROUPED tier is unreachable.

**Constructor validation** (follows `WindowPolicy` compact constructor precedent):
- `verbatimThreshold >= 0` — negative thresholds are nonsensical
- Three-tier constructor: `groupedThreshold > verbatimThreshold` — otherwise
  GROUPED is unreachable (batches jump from VERBATIM to SUMMARISED)
- Throws `IllegalArgumentException` on violation
- `summariser` — `Summariser<E, String>`: existing SPI, used for the summarised
  tier. Async via `CompletionStage`. Optional — the two-tier constructor omits
  it, and grouped handles everything above the verbatim threshold. The
  `Summariser` contract returns `List<String>` (many-to-many pipeline design).
  The renderer joins the list elements with newlines into a single summary
  string and produces one `ObservationChunk`. This lets existing pipeline
  summarisers be reused for terminal rendering without wrapping.

**Header formatting:** The renderer produces a header as part of `renderedText`.
The default header is:
```
== What Just Happened (12 seconds since your last action) ==
```

Custom headers via `withHeaderFormatter(Function<ObservationContext, String>)`:
```java
renderer.withHeaderFormatter(ctx ->
    "== Observations (" + ctx.timeSinceLastDrain() / 1000 + "s elapsed) ==");
```

Returns a new `TieredObservationRenderer` instance (immutable builder pattern).
The `ObservationContext` provides `currentTime` and `timeSinceLastDrain` for
formatting.

**Verbatim output:**
```
== What Just Happened (8 seconds since your last action) ==
- [2s ago] Dick Dastardly entered the Kitchen and said: "The combination is 7-3-9!"
- [5s ago] The Ant Hill Mob passed through and knocked over a pot.
- [8s ago] You heard Penelope call: "Has anyone seen a brass key?"
```

**Grouped output:**
```
== What Just Happened (25 seconds since your last action) ==
Dialogue: Dastardly claimed the combination is 7-3-9. Penelope asked about a brass key.
Movement: Ant Hill Mob moved to the Ballroom. Dastardly left.
Interactions: Someone tried the cabinet (locked). A pot was knocked over.
```

**Summarised output:**
```
== What Just Happened (2 minutes since your last action) ==
While you were examining the stove, the Kitchen was busy. Dastardly gave directions
(probably wrong) and left. The Ant Hill Mob passed through causing minor chaos.
Penelope is looking for a key. The cabinet remains locked.
```

### `ObservationAccumulator<E>`

```java
public class ObservationAccumulator<E> {

    public ObservationAccumulator(ObservationRenderer<E> renderer)

    public synchronized void collect(LevelEvent<E> event)
    public CompletionStage<ObservationResult> drainObservation(long now)
    public synchronized int eventCount()
    public synchronized void clear()
}
```

Thread-safe event buffer with demand-driven rendering. Contains its own
synchronized `List<LevelEvent<E>>` buffer and tracks `lastDrainTimestamp`.

**Drain lifecycle:**
1. Synchronized: atomic copy-and-clear of buffer, compute timeSinceLastDrain,
   update lastDrainTimestamp
2. Unsynchronized: delegate to renderer with the snapshot (rendering can be
   async without blocking new collects)

**Edge cases:**
- Empty buffer → `drainObservation()` returns
  `ObservationResult.empty(timeSinceLastDrain)` — a completed future wrapping
  an `ObservationResult` with empty `renderedText`, empty `chunks`,
  `eventCount = 0`, the actual `timeSinceLastDrain`, and `tier = null`.
  `lastDrainTimestamp` is NOT updated on empty drains — the next drain reports
  time since the last non-empty drain.
- First drain → `timeSinceLastDrain` = time since construction (accumulator
  records creation time).

**Thread safety model:** Same as `EventAccumulator` — drain captures a snapshot
under the lock, then releases. Events arriving during async rendering go into
the fresh buffer. No contention between collect and render.

**Failure semantics:** At-most-once delivery, consistent with the foundational
summarisation framework (#27). Events are removed from the buffer in the
synchronized block before async rendering begins. If the renderer fails
(e.g., LLM timeout in SUMMARISED tier), those events are lost. The returned
`CompletionStage` completes exceptionally, making the failure observable —
the caller can log, alert, or take compensating action. The framework makes
failure observable, not recoverable. `lastDrainTimestamp` advances on drain
regardless of renderer outcome, so `timeSinceLastDrain` always measures from
the last drain attempt, not the last successful render.

**Not tick-driven:** Unlike `SummarisationRunner.tick()`, there is no policy-based
emission check. The caller decides when to drain (the agent's turn). The
accumulator just buffers.

**Buffer growth:** The buffer is unbounded — events accumulate until drained.
For agents subscribing to high-frequency L1 buses with long inter-turn gaps,
this means memory grows linearly with event rate × idle time. The SUMMARISED
tier handles large batches gracefully (output is always bounded), but the
input buffer is not capped. Mitigation: subscribe at a higher level in the
summarisation hierarchy (L2/L3) where upstream windowing bounds the input
rate.

## Integration Patterns

### Single agent

```java
var renderer = new TieredObservationRenderer<>(
    event -> event.description(),
    event -> event.category(),
    5, 15, llmSummariser);

var accumulator = new ObservationAccumulator<>(renderer);
bus.subscribe(e -> agent.canSee(e), accumulator::collect);

// At agent's turn
ObservationResult obs = accumulator.drainObservation(now).toCompletableFuture().join();
if (obs.eventCount() > 0) {
    prompt.append(obs.renderedText());
    for (var chunk : obs.chunks()) {
        memoryStore.store(chunk, agentId, roomId);
    }
}
```

### Multi-agent (shared renderer)

```java
var renderer = new TieredObservationRenderer<>(...);  // stateless, shareable

for (var agent : agents) {
    var acc = new ObservationAccumulator<>(renderer);
    bus.subscribe(e -> agent.canSee(e), acc::collect);
    agentAccumulators.put(agent.id(), acc);
}
```

### Pipeline composition

Subscribe at any level of the summarisation hierarchy:

```java
// Low-level agent: observes raw events
l1Bus.subscribe(e -> true, lowLevelAccumulator::collect);

// Supervisory agent: observes already-summarised phases
l3Bus.subscribe(e -> true, supervisorAccumulator::collect);
```

### Two-tier (no LLM)

```java
var renderer = new TieredObservationRenderer<>(
    event -> event.description(),
    event -> event.category(),
    5);  // verbatim ≤ 5, grouped above

var accumulator = new ObservationAccumulator<>(renderer);
```

### Composition with ContextTracker

```java
var obs = accumulator.drainObservation(now).toCompletableFuture().join();
if (obs.eventCount() > 0) {
    contextTracker.addContribution(obs.renderedText().length());
}
```

## Cohesion Analysis

Verified against all blocks packages for consistency:

| Aspect | Observation design | Existing precedent | Status |
|--------|-------------------|-------------------|--------|
| `ObservationTier` as record | record(name, ordinal) | `EventLevel` record(name, ordinal) | Consistent |
| `ObservationContext` naming | `*Context` record | `ClassificationContext`, `ConvergenceContext`, `RenderContext` | Consistent |
| `ObservationRenderer` as @FunctionalInterface | Single-method interface | `Summariser`, `ConvergencePolicy` | Consistent |
| `ObservationRenderer` async return | `CompletionStage` return | `Summariser` | Consistent |
| `ObservationResult` as record | Data carrier | `AgentResult`, `ConvergenceSignal` | Consistent |
| SPI vs config for variation | SPI (generic E requires strategy) | `ConversationRenderer` uses config (fixed data model) | Justified |
| Thread safety | synchronized buffer | `EventAccumulator` synchronized | Consistent |

No structural overlap with existing code. Channel summary, conversation
rendering, and observation accumulation serve different consumers at different
levels. They compose without conflict.

## Dependencies

No new compile dependencies. Uses existing:
- `LevelEvent<E>`, `EventLevel` from `io.casehub.blocks.summarisation`
- `Summariser<E, String>` from `io.casehub.blocks.summarisation` (optional,
  for three-tier rendering)

Test dependencies: JUnit 5, AssertJ (existing).

## Test Plan

| Test class | Coverage |
|-----------|----------|
| `ObservationAccumulatorTest` | collect/drain lifecycle, empty drain → `ObservationResult.empty()` with `eventCount == 0`, empty drain does not update `lastDrainTimestamp`, time-since-last-drain tracking (first drain = time since construction), concurrent collect during async drain, renderer failure propagates via `CompletionStage`, clear, eventCount |
| `TieredObservationRendererTest` | Tier selection by batch size (verbatim, grouped, summarised), verbatim rendering with timestamps, grouped rendering with group headers and per-group chunks, summarised rendering via async Summariser with multi-element list join, two-tier construction (no summariser — grouped handles all above threshold), default header formatting with elapsed time, custom header via `withHeaderFormatter`, chunk production granularity per tier, metadata on grouped chunks (group key), constructor rejects negative verbatimThreshold, three-tier constructor rejects groupedThreshold ≤ verbatimThreshold |
| `ObservationExampleTest` | End-to-end: EventStreamBus → subscription → accumulator → drain → verify renderedText + chunks. Domain-specific events demonstrating the integration pattern. |

All plain JUnit 5 + AssertJ. No CDI, no Quarkus. Zero-dependency tests.

## Consumers

| Consumer | What it uses |
|----------|-------------|
| casehub-examples/wacky-manor | First consumer — LLM characters observe game events between turns |
| Future: quarkmind | SC2 agent observation (currently uses direct accumulator) |
| Future: IoT, AML, clinical | Domain agents observing event streams |

## Documentation Updates

- Update blocks ARC42STORIES.MD §5: add `io.casehub.blocks.summarisation.observation`
  sub-package entry under the existing `io.casehub.blocks.summarisation` section,
  listing all 7 types with their roles
- Update blocks CLAUDE.md with observation sub-package documentation

## Related

- #27 — Layered event summarisation (foundation this builds on)
- #40 — Qhorus channel integration for summarisation
- #62 — Structured progress in conversation rendering (different information
  source feeding the same agent prompt — composes at the prompt level)
