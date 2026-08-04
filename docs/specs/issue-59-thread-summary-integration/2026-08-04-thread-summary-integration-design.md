# Thread Summary Integration — Design Spec

Live `ContentSummariser<Message>` output driving per-thread summaries in qhorus,
triggered by terminal messages on correlated threads.

**Issue:** casehubio/blocks#59
**Cross-ref:** casehubio/qhorus#TBD (thread summary storage)
**Branch:** `issue-59-thread-summary-integration`

---

## Context

Channel-level summaries are complete (#64): `ChannelSummariser` implements qhorus's
`SummaryUpdateHook`, delegating to `ContentSummariser<Message>`. The scheduler sweeps
channels on a timer and fires the hook. `HeuristicMessageSummariser` is the
`@DefaultBean` — structural, zero LLM cost.

Thread-level summaries have no equivalent. Threads (messages sharing a `correlationId`)
are unbounded and implicitly created — a timer sweep doesn't scale. The summarisation
pipeline already knows when threads complete via terminal messages.

Both blockers for this work are resolved:
- qhorus#328 (threading model enrichments) — closed
- connectors#79 (inline reply threading) — closed

---

## Design

### Model: push from blocks, storage in qhorus

Channel summaries are pull-based: qhorus scheduler → hook → blocks. Thread summaries
are push-based: blocks observer detects thread completion → summarises → writes to
qhorus store. Push is structurally correct because threads are unbounded and their
completion is observable (terminal message arrives).

### Trigger: thread-completing message with correlationId

The observer fires when a DONE or FAILURE message arrives on a channel with a
non-null `correlationId`. This means the thread is complete — summarise once,
store the result.

**HANDOFF is excluded.** HANDOFF delegates the thread to a new agent — the
thread continues under a different participant. Summarising at HANDOFF would
produce a premature/partial summary that is immediately stale.

**RESPONSE is excluded.** RESPONSE is not terminal — the thread may continue
with follow-up QUERYs. The most common QUERY→RESPONSE pattern does not
receive automatic summaries. Consumers who want thread summaries for simple
request-reply threads should close them with a DONE message. This is the
correct pattern: DONE is the explicit "this thread is complete" signal.

---

## qhorus-api additions

Package: `io.casehub.qhorus.api.channel`

### ThreadSummary

```java
public record ThreadSummary(
        UUID id,
        UUID channelId,
        String correlationId,
        String content,
        Map<String, String> annotations,
        Instant updatedAt,
        String updatedBy,
        String tenancyId) {

    public ThreadSummary {
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }
}
```

Mirrors `ChannelSummary` but keyed by `channelId + correlationId`. No
`updateAfterMessages`/`updateAfterSeconds` — push-driven, not scheduled.
No `lastUpdatedMessageId` — with terminal-only trigger, there is no
incremental high-water mark. Each summarisation processes the full thread.

Builder follows the same pattern as `ChannelSummary.Builder`.

### ThreadSummaryStore

Package: `io.casehub.qhorus.api.store`

```java
public interface ThreadSummaryStore {
    ThreadSummary save(ThreadSummary summary);
    Optional<ThreadSummary> findByCorrelationId(UUID channelId, String correlationId);
    List<ThreadSummary> findByChannel(UUID channelId);
    void delete(UUID channelId, String correlationId);
}
```

No `CrossTenantThreadSummaryStore` — channelId is a globally unique UUID, so
tenant-scoped queries are sufficient. The push model has no cross-tenant sweep.

### ThreadSummaryUpdatedEvent

```java
public record ThreadSummaryUpdatedEvent(
    UUID channelId, String channelName, String correlationId, String updatedBy) {}
```

CDI event fired after a thread summary is saved. Enables UI reactivity (WebSocket
push of updated thread collapse text). Includes `channelName` for consistency
with `ChannelSummaryUpdatedEvent`.

---

## qhorus persistence

### InMemoryThreadSummaryStore

Module: `persistence-memory`

`ConcurrentHashMap<String, ThreadSummary>` keyed by `channelId:correlationId`.
Upsert on `save()` — second save for the same key overwrites.

### JPA implementation

Module: `runtime`

Entity `QhorusThreadSummary`, table `qhorus_thread_summary`. Unique constraint on
`(channel_id, correlation_id)`. `save()` uses upsert semantics
(insert-or-update by channelId + correlationId).

All qhorus changes are backward-compatible — new types only. No existing consumer
affected.

---

## blocks — ThreadSummaryObserver

Package: `io.casehub.blocks.channel.summary`

`@ApplicationScoped`. Bridges qhorus message events to `ContentSummariser<Message>`
and `ThreadSummaryStore`.

### Injection points

| Field | Type | Source |
|-------|------|--------|
| `contentSummariser` | `ContentSummariser<Message>` | blocks — reuses existing CDI point. `HeuristicMessageSummariser` is `@DefaultBean`. |
| `messageStore` | `CrossTenantMessageStore` | qhorus-api (compile dep, impl provided at deployment). Cross-tenant because the async executor may not propagate the tenancy context. |
| `threadSummaryStore` | `ThreadSummaryStore` | qhorus-api (new, impl provided at deployment) |
| `summaryEvents` | `Event<ThreadSummaryUpdatedEvent>` | CDI |
| `executor` | `ManagedExecutor` | SmallRye |
| `inFlight` | `ConcurrentHashMap.KeySetView<String, Boolean>` | Per-correlationId concurrency guard (field, not injected) |

### Observation

```java
private static final int MAX_THREAD_MESSAGES = 500;
private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

void onTerminalMessage(
        @Observes(during = TransactionPhase.AFTER_SUCCESS)
        MessageReceivedEvent event) {
    MessageType type = event.messageType();
    if (type != MessageType.DONE && type != MessageType.FAILURE) return;
    if (event.correlationId() == null) return;
    String key = event.channelId() + ":" + event.correlationId();
    if (!inFlight.add(key)) return; // already in progress
    try {
        executor.submit(() -> {
            try {
                summariseThread(event.channelId(), event.correlationId(),
                                event.channelName());
            } finally {
                inFlight.remove(key);
            }
        });
    } catch (RejectedExecutionException e) {
        inFlight.remove(key);
        LOG.log(Level.WARNING, "Executor rejected thread summary for "
                + event.correlationId(), e);
    }
}
```

**Trigger:** DONE and FAILURE only. HANDOFF excluded (thread continues
under a new agent). RESPONSE excluded (not terminal — thread may
continue). See Design section for rationale.

**Concurrency guard:** `inFlight` set prevents duplicate summarisations
when multiple terminal messages arrive for the same correlationId (e.g.,
DONE + FAILURE race). First arrival wins; subsequent arrivals skip.

**`@Observes(during = AFTER_SUCCESS)`** ensures the terminal message is
committed before we query — avoids the pre-commit race documented in
GE-20260613-6527d0.

**`executor.submit()`** makes summarisation async — never blocks message
dispatch. `RejectedExecutionException` is caught and logged — executor
saturation must not crash the observer.

### Tenant context

The async executor task runs outside the original request's tenancy
context. The observer uses `CrossTenantMessageStore` for reads (tenant-
agnostic by design). `ThreadSummaryStore.save()` receives the `tenancyId`
explicitly on the `ThreadSummary` record — the store implementation
writes it directly, not from ambient context.

### summariseThread() flow

1. Fetch thread messages: `MessageQuery.builder().channelId(channelId).correlationId(correlationId).limit(MAX_THREAD_MESSAGES).build()`
2. Read previous summary: `threadSummaryStore.findByCorrelationId(channelId, correlationId)`
3. Convert previous to `SummaryResult` (or null if none)
4. Call `contentSummariser.summarise(messages, previousResult)` → `CompletionStage<SummaryResult>`
5. Build `ThreadSummary` from result (channelId, correlationId, content, annotations, updatedAt, updatedBy, tenancyId), save to `threadSummaryStore`
6. Fire `summaryEvents.fireAsync(new ThreadSummaryUpdatedEvent(channelId, channelName, correlationId, updatedBy))`

`MAX_THREAD_MESSAGES` (500) caps the message fetch to prevent unbounded
queries on long threads. Threads exceeding this limit are summarised from
their most recent 500 messages — acceptable for a derived annotation.

### Error handling

Log and drop on failure — thread summaries are derived data, not critical path.
Consistent with `ChannelSummariser`'s approach. Most threads have exactly one
terminal message, so a failed summarisation means that thread has no summary.
Acceptable for derived data — the raw messages remain queryable.

---

## Testing

Plain JUnit 5 + Mockito. No CDI container.

### ThreadSummaryObserverTest (blocks)

| Scenario | Assertion |
|----------|-----------|
| DONE message + correlationId | Fetches thread messages, calls summariser, saves to store |
| FAILURE message + correlationId | Same as DONE — triggers summarisation |
| HANDOFF message | No summariser call, no store interaction |
| RESPONSE message | No summariser call, no store interaction |
| Non-terminal message (STATUS, QUERY) | No summariser call, no store interaction |
| Null correlationId | No summariser call, no store interaction |
| Empty thread messages | Returns previous summary unchanged (or empty SummaryResult) |
| Previous summary exists | Passed to `ContentSummariser.summarise()` as second arg |
| Summariser failure | Logged, not propagated, no store write |
| ThreadSummaryUpdatedEvent | Fired after successful save with channelName |
| Concurrent terminal messages (same correlationId) | Second is skipped (inFlight guard) |
| Executor rejection | Logged, inFlight entry cleaned up, no crash |
| tenancyId propagation | ThreadSummary saved with correct tenancyId from channel context |

### InMemoryThreadSummaryStoreTest (qhorus)

| Scenario | Assertion |
|----------|-----------|
| save + findByCorrelationId | Round-trip returns saved entity |
| Upsert semantics | Second save for same channelId+correlationId overwrites |
| findByChannel | Returns all thread summaries for a channel |
| delete | Removes the entry, subsequent find returns empty |
| Unknown thread | findByCorrelationId returns empty |

The `HeuristicMessageSummariser` tests (#64) already cover summarisation logic
for `List<Message>`. Thread summary tests verify observer wiring, not
summarisation.

---

## Cross-Repo Scope

| Step | Repo | What |
|------|------|------|
| 1 | qhorus | `ThreadSummary`, `ThreadSummaryStore`, `ThreadSummaryUpdatedEvent` in qhorus-api |
| 2 | qhorus | `InMemoryThreadSummaryStore` in persistence-memory |
| 3 | qhorus | JPA entity + store implementation in runtime |
| 4 | qhorus | `mvn install` — blocks sees updated qhorus-api |
| 5 | blocks | `ThreadSummaryObserver` + tests |

Qhorus changes are backward-compatible — new types only. Commits reference
`blocks#59`. A dedicated qhorus issue tracks the storage additions.

---

## Dependencies

**Compile (existing):** `casehub-qhorus-api` — `MessageReceivedEvent`, `MessageQuery`,
`CrossTenantMessageStore`, `Message`. Plus new: `ThreadSummary`, `ThreadSummaryStore`,
`ThreadSummaryUpdatedEvent`.

**Provided (existing):** `io.smallrye.reactive:mutiny`, `casehub-platform-agent-api`
(for LLM-backed variant activation).

**Test (existing):** `assertj`, `mockito`.

No new dependencies introduced.

---

## Design decisions

| Decision | Rationale |
|----------|-----------|
| Push from blocks, not pull from qhorus | Threads are unbounded — timer sweep doesn't scale. The observer knows when threads complete. |
| DONE + FAILURE trigger only | Clean, bounded. HANDOFF excluded — thread continues under new agent. RESPONSE excluded — not terminal, thread may continue. Consumers who want summaries for QUERY→RESPONSE threads should send DONE explicitly. |
| `@Observes(during = AFTER_SUCCESS)` + async | Ensures committed data visibility. Async avoids blocking dispatch for LLM-backed summarisers. |
| Per-correlationId concurrency guard | Prevents duplicate summarisations when multiple terminal messages race for the same thread. ConcurrentHashMap.KeySetView — lock-free, no contention on different threads. |
| `CrossTenantMessageStore` for reads | Async executor task loses tenancy context. Cross-tenant store is tenant-agnostic. tenancyId written explicitly on the ThreadSummary record. |
| No `lastUpdatedMessageId` on ThreadSummary | Terminal-only trigger processes the full thread each time. No incremental high-water mark needed. |
| MAX_THREAD_MESSAGES cap (500) | Safety net for long threads. Derived annotation — summarising recent 500 messages is acceptable. |
| Reuse `ContentSummariser<Message>` | Same SPI serves channel and thread summaries. `HeuristicMessageSummariser` works on any `List<Message>`. |
| No `CrossTenantThreadSummaryStore` | Push model has no cross-tenant sweep. channelId is globally unique. |
| No ThreadSummaryUpdateHook SPI | Push model means blocks controls timing. A hook SPI is pull infrastructure — unnecessary indirection. |
| Log-and-drop on failure | Thread summaries are derived data. Blocking dispatch or retrying aggressively for a derived annotation is disproportionate. |
| Catch `RejectedExecutionException` | Executor saturation must not crash the observer or propagate to the dispatch path. |
