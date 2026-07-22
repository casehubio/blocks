# SummaryUpdateHook Implementation

Connect blocks' summarisation intelligence to qhorus's channel summary slot.

**Issue:** casehubio/blocks#64
**Cross-ref:** casehubio/qhorus#359
**Branch:** `issue-64-summary-update-hook`

---

## Context

Qhorus #355 shipped a channel summary slot with a `SummaryUpdateHook` SPI.
The only implementation is `NoOpSummaryUpdateHook` (`@DefaultBean`) which
returns `currentSummary` unchanged. `ChannelSummaryScheduler` fires the hook
on a timer (default 60s) when message count or time thresholds are crossed.

Blocks has a summarisation pipeline (`Summariser`, `EventAccumulator`,
`SummarisationRunner`) and a channel bridge (`ChannelEventAdapter`). This
issue is the glue — a real `SummaryUpdateHook` that uses blocks'
summarisation to generate channel summaries.

---

## SPI Enrichment (qhorus-api change)

`SummaryUpdateContext` currently carries metadata but not messages:

```java
// current
record SummaryUpdateContext(
    UUID channelId, String channelName, String tenancyId,
    String currentSummary, Long lastUpdatedMessageId,
    long messagesSinceLastUpdate)
```

The hook needs message content to summarise. Two additions:

```java
// enriched
record SummaryUpdateContext(
    UUID channelId, String channelName, String tenancyId,
    String currentSummary, Long lastUpdatedMessageId,
    long messagesSinceLastUpdate,
    List<Message> recentMessages,
    Function<MessageQuery, List<Message>> messageQuery)
```

**`recentMessages`** — pre-fetched by qhorus. When `lastUpdatedMessageId`
is null (first-ever summary), all channel messages. When non-null, messages
after that ID. Covers the 90% case — the hook just uses them.

**`messageQuery`** — escape hatch for custom access patterns (sliding
window, filtered by type, re-summarisation from scratch). The function is
channel-scoped — qhorus enforces `channelId` internally. The hook builds
a `MessageQuery` without specifying the channel.

Both use existing qhorus-api types (`Message`, `MessageQuery`). blocks
already depends on qhorus-api. No new types introduced.

### Caller changes

`ChannelSummaryScheduler.updateSummary()` and
`ChannelSummaryService.triggerUpdate()` pre-fetch messages and pass a
channel-scoped query function when constructing `SummaryUpdateContext`.

Backward-compatible — `NoOpSummaryUpdateHook` ignores all fields except
`currentSummary`.

---

## Summarisation Model

The hook is an incremental fold. `currentSummary` is the accumulated state.
`recentMessages` is the new batch. The hook folds the batch into the state
and returns the new state.

This maps to a single `Summariser` invocation per hook call — no pipeline
machinery (`SummarisationRunner`, `EventAccumulator`, windowing). The hook
IS the tick.

### Summary modes

**APPEND** — treat `currentSummary` as immutable prefix, add a delta
section for new messages. Grows linearly. The only option for heuristic
summarisation.

**EDIT** — treat `currentSummary` as a draft to rewrite. New messages may
change the meaning of earlier text: an unresolved discussion becomes a
decision, a tentative plan gets confirmed, a concern gets addressed. The
summariser rewrites the whole summary to reflect the current state.

Edit mode is strictly better when the underlying writer supports rewrites
(i.e., an LLM). Surrounding text that was part of an earlier fold may need
updating because the latest fold changes its context.

```java
enum SummaryMode { APPEND, EDIT }
```

---

## Implementations

Package: `io.casehub.blocks.channel.summary`

### HeuristicChannelSummariser

`@DefaultBean @ApplicationScoped implements SummaryUpdateHook`

Append-only. Extracts structural signals from `recentMessages`:
- Participant count and names
- Message volume and time span
- Topic keywords (most frequent non-stopword terms)
- Metadata headers (via `ChannelMessageMeta.parseMeta()`)

Appends a structured delta section to `currentSummary`. When
`currentSummary` is null, produces a fresh structural summary.
Deterministic, zero cost, always available.

### LlmChannelSummariser

`@Alternative @Priority(1) @ApplicationScoped implements SummaryUpdateHook`

Edit-mode by default. Injects `AgentProvider`. Constructs a prompt from
`currentSummary` + message content from `recentMessages`. Returns a fully
rewritten summary integrating new information into the existing narrative.

Configurable via `@ConfigProperty`:
- `casehub.blocks.channel.summary.mode` — `EDIT` (default) or `APPEND`
- `casehub.blocks.channel.summary.max-tokens` — limit on summary length
- `casehub.blocks.channel.summary.system-prompt` — override default

When `currentSummary` is null, both modes behave identically — produce a
fresh summary from all messages.

Activates when `AgentProvider` is on the classpath. Applications without
LLM infrastructure get the heuristic fallback automatically via CDI
`@DefaultBean` resolution. Matches the platform pattern:
`LeastLoadedAgentStrategy` (default) vs `TrustWeightedAgentStrategy`
(alternative when ledger is present).

---

## Testing

Plain JUnit 5 + Mockito. No CDI container.

### HeuristicChannelSummariser

- Empty messages → returns `currentSummary` unchanged
- Null `currentSummary` + messages → fresh structural summary
- Messages with participants → includes participant names and count
- Messages spanning time range → includes time span
- Incremental update → appends delta, preserves existing summary
- Messages with `ChannelMessageMeta` headers → metadata extracted

### LlmChannelSummariser

- Mock `AgentProvider` — verify prompt construction, response extraction
- EDIT mode → prompt instructs rewrite of existing summary
- APPEND mode → prompt instructs delta addition only
- Null `currentSummary` → prompt omits "current summary" section
- Empty messages → returns `currentSummary` without invoking LLM
- `AgentProvider` failure → propagates exception

### SummaryUpdateContext enrichment (qhorus side)

- `ChannelSummaryScheduler` passes pre-fetched messages in context
- Null `lastUpdatedMessageId` → all channel messages pre-fetched
- `messageQuery` function is channel-scoped

---

## Cross-Repo Scope

| Step | Repo | What |
|------|------|------|
| 1 | qhorus | Enrich `SummaryUpdateContext` — add `recentMessages` + `messageQuery` |
| 2 | qhorus | Update `ChannelSummaryScheduler` + `ChannelSummaryService` to pre-fetch and pass both |
| 3 | qhorus | `mvn install` — blocks sees updated qhorus-api |
| 4 | blocks | Implement `HeuristicChannelSummariser` + `LlmChannelSummariser` with tests |

Qhorus change is backward-compatible. `NoOpSummaryUpdateHook` still works.
No migration. Commits in both repos reference `blocks#64`.

---

## Dependencies

- qhorus `SummaryUpdateHook` SPI (shipped, qhorus#355)
- qhorus `Message`, `MessageQuery` (existing qhorus-api types)
- blocks `ChannelMessageMeta` (existing, for metadata extraction)
- blocks `AgentProvider` (existing provided dependency, for LLM hook)
- blocks summarisation framework (existing, `Summariser` interface used directly)
