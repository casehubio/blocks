# Qhorus Channel Integration for Summarisation — Design Spec

**Date:** 2026-07-14
**Issue:** #40
**Status:** Draft
**Repo:** casehubio/blocks

## Problem

The summarisation framework (#27) and qhorus channels are natural
complements. Channels produce structured event streams (typed messages
with correlation chains, topics, actor attribution); the summarisation
framework produces temporal abstractions from event streams. The
integration path is bidirectional:

- **Direction 1 (channel → summarisation):** observe channel messages,
  extract domain events, feed into the summarisation pipeline
- **Direction 2 (summarisation → channel):** publish summarisation output
  as channel messages for cross-agent visibility and audit

Quarkmind's `MomentBroker` already does Direction 2 manually — subscribes
to an `EventStreamBus<GameMoment>` and dispatches to a qhorus channel as
STATUS messages. This ad-hoc pattern should be generalised.

Additionally, the summarisation framework lacks a grouping concept.
Correlation chains (COMMAND→STATUS→DONE) are natural window boundaries,
but the current `EventAccumulator` is a flat buffer with time/count
triggers. This is a general gap — clinical domains group by encounter ID,
AML groups by transaction batch — not specific to qhorus.

## Background

### Summarisation framework (blocks, #27)

Seven pure-Java types providing generic temporal abstraction:

| Type | Purpose |
|------|---------|
| `EventLevel` | `(String name, int ordinal)` — hierarchy level |
| `LevelEvent<E>` | `(E payload, long timestamp, EventLevel level)` |
| `WindowPolicy` | `(long maxAge, int maxCount)` — dual-trigger windowing |
| `EventAccumulator<E>` | Thread-safe flat buffer. `collect()`, `shouldEmit(now)`, `drain()` |
| `EventStreamBus<E>` | Predicate-based pub/sub. `subscribe()`, `publish()` |
| `Summariser<IN, OUT>` | `@FunctionalInterface`. `CompletionStage<List<OUT>> summarise(batch)` |
| `SummarisationRunner<IN, OUT>` | Wires accumulator → summariser → output bus |

Two integration patterns: **Pattern A** (SummarisationRunner pipeline —
sync heuristics, microsecond latency) and **Pattern B** (direct
EventAccumulator — async LLM dispatch, caller manages).

### Qhorus message model (qhorus-api)

| Type | Role |
|------|------|
| `MessageObserver` | Push-based observer SPI. `onMessage(MessageReceivedEvent)` |
| `MessageReceivedEvent` | Observer callback: channelName, channelId, messageType, senderId, correlationId, occurredAt, content, topic |
| `MessageDispatcher` | `dispatch(MessageDispatch) → DispatchResult` |
| `MessageDispatch` | Outbound message record with builder |
| `MessageType` | QUERY, COMMAND, RESPONSE, STATUS, DECLINE, HANDOFF, DONE, FAILURE, EVENT |

Key constraints:
- EVENT messages have null content (GE-20260607-d051f2)
- `isTerminal()` returns true for HANDOFF, DONE, FAILURE
- STATUS is the correct type for content-bearing broadcasts

### Quarkmind consumer pattern

`MomentBroker` owns Direction 2 ad-hoc:
- Subscribes to `EventStreamBus<GameMoment>` with `m -> true`
- Serializes event to JSON, dispatches as STATUS via `MessageService`
- Best-effort: catches + logs, never propagates dispatch failures

`SummarisationLifecycle` wires a two-stage pipeline:
- L2→L3: `GamePhaseSummariser` (sync, rule-based)
- L3→L4: `GameArcSummariser` (sync, rule-based)
- Both use `SummarisationRunner` with `WindowPolicy`

## Design

### Approach

Composable primitives. Two independent channel adapters (Direction 1,
Direction 2) plus a generic keyed accumulator and its runner as new
framework primitives. Each type is independently useful and testable.

### New types (4 source files)

#### 1. `KeyedAccumulator<K, E>` — `io.casehub.blocks.summarisation`

Groups events by a key, emits each group independently when a completion
predicate fires or a staleness timeout expires.

```java
public class KeyedAccumulator<K, E> {

    public KeyedAccumulator(Function<E, K> keyExtractor,
                            Predicate<List<LevelEvent<E>>> completionTest,
                            long staleTimeout)

    public synchronized void collect(LevelEvent<E> event)
    public synchronized List<List<LevelEvent<E>>> drain(long now)
    public synchronized void clear()
    public synchronized int groupCount()
    public synchronized int eventCount()
}
```

**Design decisions:**

- **`drain(long now)` replaces `shouldEmit()`/`drain()` pair.** Unlike
  `EventAccumulator` which has a single buffer, keyed accumulator has N
  groups in different states. A single `drain(now)` call checks all groups
  for completion and staleness, returns those ready, and retains the rest.

- **Returns `List<List<LevelEvent<E>>>`.** Each inner list is one
  completed group — the full event sequence for that key. Completed
  groups first (in completion order), then stale groups.

- **Thread-safe via synchronized.** Same rationale as `EventAccumulator`
  — bus callbacks run on arbitrary threads when async summarisers
  complete at non-terminal pipeline levels.

- **Staleness timeout is separate from `WindowPolicy`.** `WindowPolicy`
  governs time/count triggers on flat buffers. Staleness is a safety net
  for groups that never complete (e.g., a COMMAND with no DONE). Different
  concept, different parameter. 0 = no timeout.

- **Staleness uses clock-from-last-event semantics.** The timer resets
  each time a new event is collected for a group. "Stale" means "no new
  events within the timeout period," not "group has existed longer than
  the timeout." This prevents active chains with periodic STATUS updates
  from being force-emitted mid-flight.

- **`keyExtractor` must return non-null.** Null keys are a contract
  violation (fail-fast with `NullPointerException`). Events for which no
  meaningful key can be determined should be filtered upstream — the
  adapter's extractor returns null to skip, or the bus subscription
  predicate excludes them.

**Correlation chain usage:**

```java
// Adapter filters publisher-originated and uncorrelated messages
var adapter = new ChannelEventAdapter<ChannelEvent>(
    event -> {
        if (event.senderId().startsWith("summarisation.")) return null;  // break feedback loop
        if (event.correlationId() == null) return null;  // filter uncorrelated
        return new ChannelEvent(event.correlationId(), event.messageType(),
                                event.content(), event.topic());
    },
    new EventLevel("channel-events", 1), eventBus);

var accumulator = new KeyedAccumulator<String, ChannelEvent>(
    event -> event.correlationId(),
    group -> group.stream().anyMatch(e ->
        e.payload().messageType().isTerminal()),
    30_000L  // force-emit after 30s of inactivity (clock-from-last-event)
);
```

#### 2. `KeyedSummarisationRunner<K, IN, OUT>` — `io.casehub.blocks.summarisation`

Wires `KeyedAccumulator` → `Summariser` → output bus. Parallel to
`SummarisationRunner` for keyed accumulation.

```java
public class KeyedSummarisationRunner<K, IN, OUT> {

    public KeyedSummarisationRunner(
            Function<IN, K> keyExtractor,
            Predicate<List<LevelEvent<IN>>> completionTest,
            long staleTimeout,
            Summariser<IN, OUT> summariser,
            EventStreamBus<OUT> outputBus,
            EventLevel outputLevel)

    public void collect(LevelEvent<IN> event)
    public CompletionStage<Void> tick(long now)
    public void clear()
    public int groupCount()
    public int eventCount()
}
```

`tick(now)` drains all completed + stale groups, calls
`summariser.summarise(group)` for each, publishes results to `outputBus`.
Returns `CompletableFuture.allOf(...)` over all group futures.

**Error semantics:** Each group is summarised independently. Published
groups are not rolled back if a sibling group fails. Failed groups lose
their events — at-most-once semantics per group, consistent with
`SummarisationRunner`'s tumbling-window semantics. The returned future
fails if any group's summarisation fails, but successful groups' results
are already published. Callers that need per-group error visibility
should attach handlers to individual group futures via the `Summariser`
contract rather than relying on the aggregate `allOf`.

**Why a separate runner:** Both runners are ~40 lines. An abstraction
would add an `Accumulator<E>` interface where
`EventAccumulator.drainBatches()` awkwardly wraps its single batch. Two
simple runners is more honest than one polymorphic runner that fights
different return shapes.

#### 3. `ChannelEventAdapter<E>` — `io.casehub.blocks.channel`

Direction 1. Implements `MessageObserver`, extracts domain events,
publishes to an `EventStreamBus`.

```java
public class ChannelEventAdapter<E> implements MessageObserver {

    public ChannelEventAdapter(Function<MessageReceivedEvent, E> extractor,
                               EventLevel level,
                               EventStreamBus<E> outputBus)

    public ChannelEventAdapter(Function<MessageReceivedEvent, E> extractor,
                               EventLevel level,
                               EventStreamBus<E> outputBus,
                               Set<String> channelNames)

    @Override
    public void onMessage(MessageReceivedEvent event)

    @Override
    public Set<String> channels()

    @Override
    public Scope scope()  // LOCAL by default
}
```

**Design decisions:**

- **Extractor returns null to filter.** No separate predicate — the
  extraction function inspects the event and returns null for "not
  interesting." Avoids dual-function pattern (filter then extract).

- **Explicit extractor error handling.** The adapter wraps the extractor
  call in try/catch within `onMessage()`, logging extraction failures
  with channel name and message type context. This provides
  domain-specific error visibility rather than relying on the qhorus
  `MessageObserverDispatcher`'s generic catch-all. Failed extractions
  are silently dropped — consistent with best-effort event observation.

- **Timestamp from `occurredAt`.** `MessageReceivedEvent.occurredAt()`
  converted to epoch millis. Consistent with `LevelEvent`'s `long
  timestamp`.

- **Channel filtering via `MessageObserver.channels()`.** Qhorus
  already supports this — returning channel names means the observer
  only receives events for those channels.

#### 4. `ChannelEventPublisher<E>` — `io.casehub.blocks.channel`

Direction 2. Subscribes to an `EventStreamBus`, converts events to
`MessageDispatch`, dispatches via `MessageDispatcher`. Best-effort.

```java
public class ChannelEventPublisher<E> {

    public ChannelEventPublisher(EventStreamBus<E> inputBus,
                                 MessageDispatcher dispatcher,
                                 Function<LevelEvent<E>, MessageDispatch> messageBuilder)
}
```

Constructor subscribes to `inputBus` with `e -> true`. On each event:
calls `messageBuilder` — if it returns null the event is skipped (soft
disable mechanism), otherwise dispatches via `dispatcher`, catches + logs
any exception. Never propagates dispatch failures.

**Lifecycle:** Application-scoped, matches the bus. `EventStreamBus` has
no `unsubscribe()` by design (#27 spec: "No consumer ever removes
individual subscriptions"). The publisher lives for the CDI lifetime.
If a domain needs to stop publishing, it stops ticking the pipeline.

### Pipeline composition

Full channel-to-summarisation pipeline with correlation-aware windowing.
`ChannelEvent` in these examples is a domain-specific projection —
whatever record the consumer defines to carry the fields relevant to
their summarisation logic. A minimal projection for correlation chain
usage:

```java
record ChannelEvent(String correlationId, MessageType messageType,
                    String content, String topic) {}
```

```
MessageObserver registration (CDI or manual)
  │
  ▼
ChannelEventAdapter<ChannelEvent>          ← Direction 1
  │  extracts + classifies domain event from MessageReceivedEvent
  │  publishes LevelEvent<ChannelEvent> at L1
  ▼
EventStreamBus<ChannelEvent>               ← L1 bus
  │
  ▼
KeyedSummarisationRunner<String, ChannelEvent, Episode>
  │  groups by correlationId
  │  emits when terminal message arrives (or stale timeout)
  │  summariser produces Episode from completed chain
  ▼
EventStreamBus<Episode>                    ← L2 bus
  │
  ├──▶ SummarisationRunner<Episode, Phase>     ← existing Pattern A
  │      WindowPolicy(maxAge=..., maxCount=...)
  │      ▼
  │    EventStreamBus<Phase>               ← L3 bus
  │      ▼
  │    SummarisationRunner<Phase, Narrative>
  │      ▼
  │    EventStreamBus<Narrative>           ← L4 bus
  │
  └──▶ ChannelEventPublisher<Episode>      ← Direction 2
         serializes Episode → MessageDispatch
         dispatches to qhorus channel for visibility
```

The adapter performs the L0→L1 transition: raw `MessageReceivedEvent`
(L0) is extracted and classified into a domain event (L1). This is
consistent with the #27 hierarchy where L1 = "classified events" and
with the clinical example where L1 vitals are the framework entry point.
Issue #40's L1 classification (obligation vs information exchange) is
naturally handled by the extractor — it can filter by message type and
return null for events that don't participate in the pipeline.

The keyed runner shares the same wiring pattern as `SummarisationRunner`
— same `collect()`/`tick()` API shape, same bus subscription via method
reference. Downstream stages are unaffected by whether L2 events came
from grouped correlation chains or flat windows. The runners are not
type-substitutable (observation APIs differ: `size()` vs
`groupCount()`/`eventCount()`), but their compositional role in the
pipeline is identical.

**Multi-topic pipelines:** When the adapter receives events from
multiple topics (3-arg constructor), correlation IDs may not be unique
across topics. Use a composite key if topic isolation is required:
`event -> event.topic() + ":" + event.correlationId()`.

### Channel-as-viewer pattern

Standard wiring recipe for rendering any summarisation pipeline through
qhorus channels (issue #40 Direction 2). Each level maps to a qhorus
structural concept:

| Summarisation Level | Qhorus Rendering | How |
|---------------------|-----------------|-----|
| L1 classified events | Messages in a topic | STATUS messages in a domain-specific topic |
| L2 episodes | Threads (correlation chains) | STATUS messages carrying the episode's `correlationId` |
| L3 phases | Topics | STATUS messages with `topic` set to the phase name |
| L4 narratives | Channel-level summary | STATUS messages from a SYSTEM actor in the default topic |

Wire one `ChannelEventPublisher` per level, each with a level-appropriate
`messageBuilder`:

```java
// L2 episodes → threads (carries correlationId for threading)
new ChannelEventPublisher<>(episodeBus, dispatcher,
    event -> MessageDispatch.builder()
        .channelId(channelId)
        .sender("summarisation.episodes")
        .actorType(ActorType.AGENT)
        .type(MessageType.STATUS)
        .topic("episodes")
        .correlationId(event.payload().correlationId())
        .content(serialize(event.payload()))
        .build());

// L3 phases → topics (phase name becomes the topic)
new ChannelEventPublisher<>(phaseBus, dispatcher,
    event -> MessageDispatch.builder()
        .channelId(channelId)
        .sender("summarisation.phases")
        .actorType(ActorType.AGENT)
        .type(MessageType.STATUS)
        .topic(event.payload().phaseName())
        .content(serialize(event.payload()))
        .build());

// L4 narratives → channel-level summary
new ChannelEventPublisher<>(narrativeBus, dispatcher,
    event -> MessageDispatch.builder()
        .channelId(channelId)
        .sender("summarisation.narrative")
        .actorType(ActorType.SYSTEM)
        .type(MessageType.STATUS)
        .content(event.payload().summary())
        .build());
```

The publisher is intentionally flat — the structural mapping lives in
the `messageBuilder` function, not in the publisher type. This keeps
the publisher a reusable primitive while the pattern section documents
how to compose level-specific routing. Consumers wire one publisher per
level they want visible in the channel.

**Same-channel feedback loop:** When Direction 1 and Direction 2 operate
on the same channel, the published STATUS messages re-enter the adapter
via `MessageObserverDispatcher`. Without filtering, this creates an
infinite loop: each summary produces a new event → new summary → repeat.

Break the loop with a sender filter in the adapter's extractor:

```java
event -> {
    if (event.senderId().startsWith("summarisation.")) return null;
    // ... rest of extraction
}
```

The publisher senders (`"summarisation.episodes"`,
`"summarisation.phases"`, `"summarisation.narrative"`) use a consistent
prefix. The extractor filters these before extraction, preventing
re-entry. This is the standard pattern when both directions share a
channel — the extractor already handles filtering (null correlationId,
unwanted message types), and sender filtering is one more line in the
same function.

### Quarkmind migration path

`MomentBroker`'s manual `dispatchToQhorus()` replaced by
`ChannelEventPublisher`:

```java
// Before (manual)
momentBus.subscribe(m -> true, this::dispatchToQhorus);

// After
new ChannelEventPublisher<>(momentBus, messageService::dispatch,
    event -> MessageDispatch.builder()
        .channelId(channelId)
        .sender("summarisation.moment-broker")
        .actorType(ActorType.AGENT)
        .type(MessageType.STATUS)
        .content(serialize(event))
        .build());
```

The publisher encapsulates the best-effort dispatch pattern (catch + log,
never propagate) that MomentBroker currently does manually. Quarkmind
migration is out of scope for this issue but the API is designed to
support it without changes.

### Deliverable scoping

| Deliverable | Scope |
|-------------|-------|
| Adapters + keyed accumulator + runner | Full implementation |
| Channel-as-viewer pattern (issue deliverable 2) | Documented as pattern section + demonstrated by integration test |
| Thread summary integration (issue deliverable 3) | Deferred to #59 — depends on connectors UI in-flight |

Issue #40 will be closed after this work. Deliverable 1 is fully addressed,
deliverable 2 is addressed by the pattern section and integration test,
deliverable 3 is tracked separately as #59.

### Dependencies

No new compile dependencies. `ChannelEventAdapter` implements
`MessageObserver` and receives `MessageReceivedEvent` — both in
`casehub-qhorus-api` (already compile scope). `ChannelEventPublisher`
uses `MessageDispatcher` and `MessageDispatch` — also `casehub-qhorus-api`.
`KeyedAccumulator` and `KeyedSummarisationRunner` are pure Java.

### Garden gotchas addressed

- **GE-20260607-d051f2** (EVENT messages have null content):
  `ChannelEventAdapter` handles this — the extractor receives the full
  `MessageReceivedEvent` and can return null for EVENT messages. Tested
  explicitly.
- **GE-20260623-ef0e7c** (typed channel asymmetry): not directly
  relevant — the adapter observes, doesn't dispatch typed messages. The
  publisher uses STATUS (same as quarkmind's MomentBroker pattern).

## Testing

### Unit tests (plain JUnit 5 + AssertJ, no CDI)

| Test class | Coverage |
|------------|----------|
| `KeyedAccumulatorTest` | Group creation, completion predicate trigger, stale timeout, drain returns completed + stale, clear, concurrent collect safety, empty drain, single-event groups, multi-key interleaving |
| `KeyedSummarisationRunnerTest` | Tick drains completed groups, summariser called per group, output published at correct level, no-emit when no groups complete, stale groups emitted, async summariser error propagation via tick() return, clear delegation |
| `ChannelEventAdapterTest` | Extractor called with event, null return filters, LevelEvent published with correct timestamp/level, channel name filtering via channels(), EVENT messages with null content handled |
| `ChannelEventPublisherTest` | Subscribes on construction, messageBuilder called per event, dispatch exceptions caught and logged, messageBuilder returning null skips dispatch |

### Integration example test

```
src/test/java/io/casehub/blocks/channel/examples/summarisation/
    ChannelSummarisationPipelineTest.java
```

Full pipeline: `ChannelEventAdapter` → `KeyedSummarisationRunner`
(correlation grouping) → `SummarisationRunner` (phase windowing) →
`ChannelEventPublisher`. Demonstrates:

- Both directions in one pipeline
- Correlation chain completion triggers L2 episode
- Stale chain timeout forces emission
- Phase windowing at L3 uses existing `SummarisationRunner`
- Publisher captures output dispatches for assertion

Follows the clinical/logistics example pattern from #27.

## Consumers

| Repo | Usage |
|------|-------|
| quarkmind | Direction 2: `ChannelEventPublisher` replaces `MomentBroker.dispatchToQhorus()` |
| IoT | Direction 1: sensor events from channels → summarisation pipeline |
| clinical | Direction 1: patient vital updates from channels → care phase pipeline |
| AML | Direction 1: transaction notifications from channels → activity pattern pipeline |
| All of the above | `KeyedAccumulator` for domain-specific grouping (encounter ID, transaction batch, sensor session) |

## Design decisions

| Decision | Rationale |
|----------|-----------|
| Two adapters, not one bidirectional bridge | Directions are independent concerns — no shared state. Domains may use only one direction. |
| Generic `KeyedAccumulator`, not qhorus-specific | Correlation chains are one instance of keyed grouping. Clinical groups by encounter, AML by batch. |
| Separate `KeyedSummarisationRunner` | Two ~40-line runners beats one abstract runner with strategy indirection. |
| Extractor returns null to filter | Avoids dual-function (filter then extract). Extraction already inspects the event. |
| Publisher has no `close()` | `EventStreamBus` has no `unsubscribe()` by design. Application-scoped lifecycle. |
| Staleness timeout separate from `WindowPolicy` | Different concept — safety net for incomplete groups, not a windowing trigger. |
| `drain(long now)` single method | Keyed accumulator has N groups — `shouldEmit()`/`drain()` pair doesn't work. One call checks all groups. |
| `keyExtractor` must return non-null | Null keys are a contract violation. Events with no meaningful key should be filtered upstream (adapter extractor returns null, or bus subscription predicate). |
| Staleness uses clock-from-last-event | Timer resets on each new event for a group. "Stale" = no activity, not "group existed too long." Prevents active chains with periodic updates from being force-emitted mid-flight. |
| Per-group at-most-once error semantics | Each group is summarised independently. Failed groups lose their events. Successful groups' results are published regardless of sibling failures. Consistent with `SummarisationRunner`'s tumbling-window semantics. |
| `DispatchResult` advisories not observed | Consistent with established `MomentBroker` pattern. The publisher is best-effort; advisory logging can be added if operationally needed. |

## Related issues

- #27 — summarisation framework extraction (done, prerequisite)
- qhorus#328 — Space, Topic, threading model enrichments (done, prerequisite)
- #41 — generalised summarisation pipeline viewer (future — UI complement)
