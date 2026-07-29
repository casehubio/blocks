# Summarisation → Qhorus Channel Summary Integration

**Issue:** casehubio/qhorus#359
**Branch:** `issue-359-summarisation-qhorus-integration`
**Scope:** Cross-repo — qhorus-api SPI change + blocks re-architecture

## Problem

Blocks has two independent summarisation subsystems that don't compose:

1. **`blocks.summarisation`** — temporal abstraction framework (push-based pipeline). Pure Java. `Summariser<IN, OUT>` operates on `LevelEvent<IN>`. Used by quarkmind for SC2 game events.

2. **`blocks.channel.summary`** — two standalone `SummaryUpdateHook` implementations (pull-based hook). `HeuristicChannelSummariser` and `LlmChannelSummariser` implement the qhorus SPI directly without using the framework.

The summarisation algorithms (heuristic extraction, LLM synthesis) are locked inside hook implementations. They can't be reused in pipeline contexts, composed together, or adapted per-invocation based on batch size. The qhorus SPI returns `String`, preventing structured output.

## Design Principle

Each layer owns the abstraction that belongs to its concern:

| Layer | Owns | Does NOT own |
|-------|------|-------------|
| **qhorus-api** | Channel lifecycle: *when* to summarise, *what* data is available, *where* the result is stored | Summarisation algorithms, strategy selection |
| **blocks.summarisation** | Reusable algorithms: *how* to summarise, strategy composition, tiered dispatch | Channel-specific rendering, qhorus SPI adaptation |
| **blocks.channel.summary** | Message-specific rendering, qhorus SPI adaptation | General summarisation algorithms |

## Layer 1: qhorus-api — Enriched SPI

### SummaryResult

New record replacing `String` as the hook return type:

```java
package io.casehub.qhorus.api.spi;

public record SummaryResult(String text, Map<String, String> annotations) {
    public SummaryResult {
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }
    public static SummaryResult ofText(String text) {
        return new SummaryResult(text, Map.of());
    }
}
```

- `text` — human-readable summary for the channel slot and MCP tools
- `annotations` — extensible key-value metadata. qhorus stores it alongside the text but does not interpret it. Blocks uses it for tier tracking, participant lists, topic lists, message counts.

### SummaryUpdateHook

Returns `SummaryResult` instead of `String`:

```java
@FunctionalInterface
public interface SummaryUpdateHook {
    SummaryResult update(SummaryUpdateContext context);
}
```

### SummaryUpdateContext

`currentSummary` (String) is replaced with `previousResult` (SummaryResult). A convenience `currentSummary()` method preserves ergonomics for simple hooks:

```java
public record SummaryUpdateContext(
    UUID channelId,
    String channelName,
    String tenancyId,
    @Nullable SummaryResult previousResult,
    @Nullable Long lastUpdatedMessageId,
    long messagesSinceLastUpdate,
    List<Message> recentMessages,
    Function<MessageQuery, List<Message>> messageQuery
) {
    public @Nullable String currentSummary() {
        return previousResult != null ? previousResult.text() : null;
    }
}
```

### NoOpSummaryUpdateHook

Updated to pass through the previous result:

```java
@DefaultBean
@ApplicationScoped
public class NoOpSummaryUpdateHook implements SummaryUpdateHook {
    @Override
    public SummaryResult update(SummaryUpdateContext context) {
        return context.previousResult() != null
            ? context.previousResult()
            : SummaryResult.ofText("");
    }
}
```

### Downstream impact

The `SummaryUpdateContext` record constructor changes. Any qhorus code that creates `SummaryUpdateContext` instances must pass `SummaryResult` instead of `String` for the current summary field. Pre-release — no backward compatibility concern.

### Qhorus Runtime Changes

The qhorus runtime must store and round-trip annotations for cross-invocation state to work.

**`ChannelSummary`** gains `Map<String, String> annotations`:

```java
public record ChannelSummary(UUID id, UUID channelId, String content,
    Map<String, String> annotations, Instant updatedAt, String updatedBy,
    Long lastUpdatedMessageId, Integer updateAfterMessages,
    Integer updateAfterSeconds, String tenancyId) {
    public ChannelSummary {
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }
}
```

**`ChannelSummaryEntity`** gains a JSON-serialized annotations column:

```java
@Column(name = "annotations", columnDefinition = "TEXT")
public String annotations; // JSON — {"participants":"alice,bob","tier":"grouped"}
```

`fromDomain()` serializes `Map<String, String>` to JSON. `toDomain()` deserializes back. Jackson `ObjectMapper` or a manual `Map.toString()`/parse — implementation choice.

**`ChannelSummaryService.triggerUpdate()`** reconstructs `SummaryResult` from stored data and persists the full result:

```java
var previousAnnotations = existing.annotations() != null ? existing.annotations() : Map.of();
var previousResult = existing.content() != null
    ? new SummaryResult(existing.content(), previousAnnotations)
    : null;
SummaryResult updated = hook.update(new SummaryUpdateContext(
    channelId, ch.name(), ch.tenancyId(), previousResult, ...));
summaryStore.save(existing.toBuilder()
    .content(updated.text())
    .annotations(updated.annotations())
    .build());
```

**`QhorusMcpToolsBase.ChannelSummaryResult`** gains annotations (all existing fields preserved):

```java
record ChannelSummaryResult(String channelName, String content,
    Map<String, String> annotations, String updatedAt, String updatedBy,
    Integer updateAfterMessages, Integer updateAfterSeconds) {}
```

**Migration:** Flyway migration adds `annotations TEXT` column (nullable, defaults null). Existing rows have null annotations which `ChannelSummary`'s compact constructor normalizes to `Map.of()`.

## Layer 2: blocks.summarisation — Reusable Algorithm SPI

### ContentSummariser<T>

The shared algorithm abstraction. Both hook and pipeline can use it via thin adapters.

```java
package io.casehub.blocks.summarisation;

@FunctionalInterface
public interface ContentSummariser<T> {
    CompletionStage<SummaryResult> summarise(
        List<T> items, @Nullable SummaryResult previous);
}
```

Design choices:

1. **`List<T>` not `List<LevelEvent<T>>`** — decoupled from pipeline event model. Works with raw messages, domain events, or any content type.
2. **Returns `SummaryResult`** — same type as qhorus SPI return. blocks already depends on qhorus-api, so reusing the type avoids a near-duplicate with a pointless adapter. Every consumer of blocks has qhorus-api transitively.
3. **`@Nullable SummaryResult previous`** — enables running-summary lifecycle. First invocation: null. Subsequent: previous output. Implementations decide how to merge text (append, edit, ignore). **Annotation contract:** implementations SHOULD start from `previous.annotations()` as a base map and overlay only the keys they own. This ensures unknown annotation keys survive tier transitions in composed pipelines (e.g., a domain-specific `urgency` key set by one tier is not dropped when a different tier handles the next batch).
4. **`CompletionStage`** — LLM-backed implementations are async. Sync implementations wrap in `CompletableFuture.completedFuture()`.

### SummaryMode

Moved from `blocks.channel.summary` to `blocks.summarisation` — it's a general summarisation concept:

```java
package io.casehub.blocks.summarisation;

public enum SummaryMode {
    APPEND,
    EDIT
}
```

### VerbatimContentSummariser<T>

Renders each item individually as a bullet list. Preserves both text and annotations from `previous` when present — appends verbatim items after the accumulated text and propagates all previous annotations, overlaying its own (`tier`, `itemCount`). This makes it safe as a tier in `TieredContentSummariser` for running-summary contexts: small batches append verbatim items and carry forward accumulated metadata rather than destroying state.

```java
package io.casehub.blocks.summarisation;

public class VerbatimContentSummariser<T> implements ContentSummariser<T> {
    private final Function<T, String> renderer;

    public VerbatimContentSummariser(Function<T, String> renderer) {
        this.renderer = renderer;
    }

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<T> items, @Nullable SummaryResult previous) {
        var sb = new StringBuilder();
        if (previous != null && !previous.text().isBlank()) {
            sb.append(previous.text()).append("\n\n");
        }
        for (var item : items) {
            sb.append("- ").append(renderer.apply(item)).append('\n');
        }
        var annotations = new HashMap<>(
            previous != null ? previous.annotations() : Map.of());
        annotations.put("tier", "verbatim");
        annotations.put("itemCount", String.valueOf(items.size()));
        return CompletableFuture.completedFuture(
            new SummaryResult(sb.toString().stripTrailing(), annotations));
    }
}
```

### LlmContentSummariser<T>

LLM-powered synthesis via `AgentProvider`. Generic on `T` — renders items via a function, sends to LLM with system prompt and current summary context.

Lives in `blocks.summarisation.llm` — separate from the pure-Java `blocks.summarisation` package because it depends on `AgentProvider` (platform-agent) and Mutiny. The parent package's "Pure Java, zero CDI/Quarkus dependencies" characterization is preserved.

An optional `preamble` provides static context for the LLM prompt (e.g., `"Channel: design-review"`). This replaces the channel-name context that the old `LlmChannelSummariser.buildUserPrompt()` included directly from `SummaryUpdateContext`. Since `LlmContentSummariser<T>` is generic, per-invocation context like channel name must be provided at construction time.

```java
package io.casehub.blocks.summarisation.llm;

public class LlmContentSummariser<T> implements ContentSummariser<T> {
    private static final System.Logger LOG =
        System.getLogger(LlmContentSummariser.class.getName());

    private final AgentProvider agentProvider;
    private final Function<T, String> renderer;
    private final SummaryMode mode;
    private final @Nullable String preamble;

    // System prompts carried over from LlmChannelSummariser:
    // EDIT: "...produce an updated summary that integrates the new information.
    //        You may rewrite any part of the existing summary..."
    // APPEND: "...append a brief update section summarising the new messages.
    //          Do not modify the existing summary..."

    public LlmContentSummariser(AgentProvider agentProvider,
                                 Function<T, String> renderer,
                                 SummaryMode mode,
                                 @Nullable String preamble) {
        this.agentProvider = agentProvider;
        this.renderer = renderer;
        this.mode = mode;
        this.preamble = preamble;
    }

    public LlmContentSummariser(AgentProvider agentProvider,
                                 Function<T, String> renderer,
                                 SummaryMode mode) {
        this(agentProvider, renderer, mode, null);
    }

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<T> items, @Nullable SummaryResult previous) {
        String systemPrompt = mode == SummaryMode.EDIT ? EDIT_PROMPT : APPEND_PROMPT;
        String userPrompt = buildPrompt(items, previous);
        var config = AgentSessionConfig.of(systemPrompt, userPrompt);

        return agentProvider.invoke(config)
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .collect().with(Collectors.joining())
            .map(text -> {
                var annotations = new HashMap<>(
                    previous != null ? previous.annotations() : Map.of());
                annotations.put("tier", "synthesised");
                annotations.put("itemCount", String.valueOf(items.size()));
                return new SummaryResult(text, annotations);
            })
            .convert().toCompletionStage();
    }

    private String buildPrompt(List<T> items, @Nullable SummaryResult previous) {
        var sb = new StringBuilder();
        if (preamble != null && !preamble.isBlank()) {
            sb.append(preamble).append("\n\n");
        }
        if (previous != null && !previous.text().isBlank()) {
            sb.append("Current summary:\n").append(previous.text()).append("\n\n");
        }
        sb.append("New items (").append(items.size()).append("):\n");
        for (T item : items) {
            sb.append(renderer.apply(item)).append('\n');
        }
        return sb.toString();
    }
}
```

### TieredContentSummariser<T>

Dispatches to delegates based on batch size thresholds. Composes other `ContentSummariser<T>` instances.

```java
package io.casehub.blocks.summarisation;

public class TieredContentSummariser<T> implements ContentSummariser<T> {
    private final ContentSummariser<T> small;
    private final ContentSummariser<T> medium;
    private final ContentSummariser<T> large;
    private final int smallThreshold;
    private final int mediumThreshold;

    // 2-tier constructor (small + large)
    public TieredContentSummariser(
            ContentSummariser<T> small,
            ContentSummariser<T> large,
            int smallThreshold) {
        this(small, large, large, smallThreshold, smallThreshold);
    }

    // 3-tier constructor (small + medium + large)
    public TieredContentSummariser(
            ContentSummariser<T> small,
            ContentSummariser<T> medium,
            ContentSummariser<T> large,
            int smallThreshold,
            int mediumThreshold) {
        // validation: smallThreshold > 0, mediumThreshold >= smallThreshold
        this.small = small;
        this.medium = medium;
        this.large = large;
        this.smallThreshold = smallThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<T> items, @Nullable SummaryResult previous) {
        if (items.size() <= smallThreshold) return small.summarise(items, previous);
        if (items.size() <= mediumThreshold) return medium.summarise(items, previous);
        return large.summarise(items, previous);
    }
}
```

### ContentSummariserToSummariser<T> — Pipeline adapter

Allows `ContentSummariser<T>` algorithms to be used in a `SummarisationRunner` pipeline:

```java
package io.casehub.blocks.summarisation;

public class ContentSummariserToSummariser<T> implements Summariser<T, String> {
    private final ContentSummariser<T> delegate;

    public ContentSummariserToSummariser(ContentSummariser<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletionStage<List<String>> summarise(List<LevelEvent<T>> batch) {
        List<T> items = batch.stream().map(LevelEvent::payload).toList();
        return delegate.summarise(items, null)
            .thenApply(result -> List.of(result.text()));
    }
}
```

`previous` is null because pipeline batches are independent — there's no running summary in a `SummarisationRunner` tick.

**Annotation loss:** The pipeline path discards `SummaryResult` annotations — `Summariser<T, String>` outputs `String`, so only `result.text()` survives. The same `ContentSummariser` implementation delivers full `SummaryResult` through the hook path but only plain text through the pipeline path. This is inherent to the pipeline model's `String` output type and is an accepted limitation of algorithm reuse across the two paths.

## Layer 3: blocks.channel.summary — Message-Specific + Adaptation

### HeuristicMessageSummariser

`ContentSummariser<Message>` that extracts structural metadata from qhorus messages. Always APPEND mode — appends a delta section to the previous summary text.

```java
package io.casehub.blocks.channel.summary;

@DefaultBean
@ApplicationScoped
public class HeuristicMessageSummariser implements ContentSummariser<Message> {

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<Message> messages, @Nullable SummaryResult previous) {
        var sb = new StringBuilder();
        if (previous != null && !previous.text().isBlank()) {
            sb.append(previous.text()).append("\n\n");
        }
        sb.append("--- Update (").append(messages.size()).append(" messages) ---\n");

        // Extract participants
        var participants = messages.stream()
            .map(Message::sender).filter(Objects::nonNull).distinct().toList();
        if (!participants.isEmpty()) {
            sb.append("Participants: ").append(String.join(", ", participants)).append('\n');
        }

        // Extract time period
        var first = messages.getFirst().createdAt();
        var last = messages.getLast().createdAt();
        if (first != null && last != null) {
            sb.append("Period: ").append(first).append(" — ").append(last).append('\n');
        }

        // Extract topics
        var topics = messages.stream()
            .map(Message::topic).filter(t -> t != null && !t.isBlank()).distinct().toList();
        if (!topics.isEmpty()) {
            sb.append("Topics: ").append(String.join(", ", topics)).append('\n');
        }

        // Merge annotations from previous invocation
        var allParticipants = new LinkedHashSet<String>();
        var allTopics = new LinkedHashSet<String>();
        if (previous != null) {
            var prev = previous.annotations();
            if (prev.containsKey("participants")) {
                allParticipants.addAll(List.of(prev.get("participants").split(",")));
            }
            if (prev.containsKey("topics")) {
                allTopics.addAll(List.of(prev.get("topics").split(",")));
            }
        }
        allParticipants.addAll(participants);
        allTopics.addAll(topics);

        var annotations = new HashMap<>(
            previous != null ? previous.annotations() : Map.of());
        annotations.put("tier", "grouped");
        annotations.put("itemCount", String.valueOf(messages.size()));
        if (!allParticipants.isEmpty()) {
            annotations.put("participants", String.join(",", allParticipants));
        }
        if (!allTopics.isEmpty()) {
            annotations.put("topics", String.join(",", allTopics));
        }

        return CompletableFuture.completedFuture(
            new SummaryResult(sb.toString().stripTrailing(), annotations));
    }
}
```

### ChannelSummariser

The single `SummaryUpdateHook` implementation. Delegates to an injected `ContentSummariser<Message>`.

```java
package io.casehub.blocks.channel.summary;

@ApplicationScoped
public class ChannelSummariser implements SummaryUpdateHook {
    private static final System.Logger LOG =
        System.getLogger(ChannelSummariser.class.getName());

    private final ContentSummariser<Message> delegate;

    @Inject
    public ChannelSummariser(ContentSummariser<Message> delegate) {
        this.delegate = delegate;
    }

    @Override
    public SummaryResult update(SummaryUpdateContext context) {
        if (context.recentMessages() == null || context.recentMessages().isEmpty()) {
            return context.previousResult() != null
                ? context.previousResult()
                : SummaryResult.ofText("");
        }
        try {
            return Uni.createFrom()
                .completionStage(delegate.summarise(
                    context.recentMessages(), context.previousResult()))
                .await().indefinitely();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                "Summarisation failed for channel " + context.channelName()
                    + " (" + context.recentMessages().size() + " messages)", e);
            throw e;
        }
    }
}
```

Uses Mutiny's `.await().indefinitely()` instead of `CompletableFuture.join()` — detects Vert.x event-loop threads and fails fast with a clear error instead of deadlocking silently. Error handling logs channel name and message count (channel context available via `SummaryUpdateContext`), preserving the operational debuggability from the existing `LlmChannelSummariser`.

### CDI Wiring

Blocks provides `HeuristicMessageSummariser` as `@DefaultBean`:

```java
@DefaultBean
@ApplicationScoped
public class HeuristicMessageSummariser implements ContentSummariser<Message> { ... }
```

Domain repos that want tiered summarisation produce a `ContentSummariser<Message>` bean. Config properties from the prior `LlmChannelSummariser` are preserved via CDI injection:

```java
// In a domain repo
@ApplicationScoped
public class ChannelSummaryConfig {
    @Produces
    ContentSummariser<Message> channelSummariser(
            AgentProvider agentProvider,
            @ConfigProperty(name = "casehub.blocks.channel.summary.mode",
                            defaultValue = "EDIT")
            SummaryMode mode) {
        var heuristic = new HeuristicMessageSummariser();
        var llm = new LlmContentSummariser<>(agentProvider,
            msg -> "[" + msg.sender() + "] " + msg.content(),
            mode,
            "Channel: message-summary");
        return new TieredContentSummariser<>(heuristic, llm, 5);
    }
}
```

The three config properties from `LlmChannelSummariser` map to CDI producer parameters:

| Prior config property | How it maps |
|---|---|
| `casehub.blocks.channel.summary.mode` | `@ConfigProperty` → `SummaryMode` parameter |
| `casehub.blocks.channel.summary.max-tokens` | Deferred — not yet used by current implementation |
| `casehub.blocks.channel.summary.system-prompt` | Deferred — custom system prompts require `LlmContentSummariser` constructor extension |

### Removed

- `HeuristicChannelSummariser` — replaced by `HeuristicMessageSummariser` + `ChannelSummariser`
- `LlmChannelSummariser` — replaced by `LlmContentSummariser<T>` + `ChannelSummariser`

## Relationship to TieredObservationRenderer

`TieredObservationRenderer<E>` and `TieredContentSummariser<T>` use the same dispatch pattern (3-line if/else on batch size). This is NOT meaningful duplication — the strategies, types, and semantics differ:

| | TieredObservationRenderer | TieredContentSummariser |
|---|---|---|
| Purpose | Render events for LLM prompts | Produce running summaries |
| Statefulness | Stateless snapshot | Stateful (previous + delta) |
| Input | `List<LevelEvent<E>>` | `List<T>` |
| Output | `ObservationResult` (text + chunks + tier) | `SummaryResult` (text + annotations) |

Both remain as parallel implementations. No shared dispatch abstraction is needed — the 3-line if/else is trivial.

## Package Structure After

```
blocks.summarisation/
├── EventLevel, LevelEvent, WindowPolicy       (unchanged — pipeline model)
├── EventAccumulator, KeyedAccumulator          (unchanged — pipeline buffering)
├── Summariser<IN, OUT>                         (unchanged — pipeline algorithm SPI)
├── SummarisationRunner, KeyedRunner            (unchanged — pipeline wiring)
├── EventStreamBus                              (unchanged — pipeline pub/sub)
├── SummaryMode                                 (moved from channel.summary)
├── ContentSummariser<T>                        (NEW — reusable algorithm SPI)
├── VerbatimContentSummariser<T>                (NEW)
├── TieredContentSummariser<T>                  (NEW — composes delegates)
├── ContentSummariserToSummariser<T>            (NEW — pipeline adapter)
├── llm/                                        (NEW sub-package — CDI/Mutiny deps)
│   └── LlmContentSummariser<T>                 (NEW — needs AgentProvider)
├── observation/                                (unchanged)
│   ├── TieredObservationRenderer               (unchanged)
│   ├── ObservationAccumulator                  (unchanged)
│   └── ...
└── observation/affordance/                     (unchanged)

blocks.channel.summary/
├── HeuristicMessageSummariser                  (NEW — ContentSummariser<Message>)
├── ChannelSummariser                           (NEW — SummaryUpdateHook adapter)
```

## Composition Examples

### Hook context — domain repo deploys tiered summarisation

```java
// Wiring (CDI or manual)
var verbatim = new VerbatimContentSummariser<>(
    msg -> "[" + msg.sender() + "] " + msg.content());
var heuristic = new HeuristicMessageSummariser();
var llm = new LlmContentSummariser<>(agentProvider,
    msg -> "[" + msg.sender() + "] " + msg.content(), SummaryMode.EDIT,
    "Channel: " + channelName);
var tiered = new TieredContentSummariser<>(verbatim, heuristic, llm, 5, 20);

// qhorus fires the hook → ChannelSummariser → tiered → appropriate strategy
```

### Pipeline context — reuse the same algorithm

```java
// Same heuristic, used in a SummarisationRunner pipeline
var heuristic = new HeuristicMessageSummariser();
Summariser<Message, String> pipelineSummariser =
    new ContentSummariserToSummariser<>(heuristic);
var runner = new SummarisationRunner<>(
    new WindowPolicy(0, 10), pipelineSummariser, outputBus, outputLevel);
```

### Structured annotations — cross-invocation state

Annotations accumulate across invocations. `HeuristicMessageSummariser` reads `previous.annotations()`, unions participant and topic sets, and writes the merged result:

```java
// First invocation: hook fires with 5 messages from alice, bob about "caching"
// → heuristic produces SummaryResult(text, {participants: "alice,bob", topics: "caching"})
// → qhorus stores both text and annotations (see §Qhorus Runtime Changes)

// Second invocation: hook fires with 3 more messages from bob, carol about "indexing"
// → heuristic reads previous.annotations().get("participants") → "alice,bob"
// → unions with current participants → "alice,bob,carol"
// → reads previous.annotations().get("topics") → "caching"
// → unions with current topics → "caching,indexing"
// → produces SummaryResult(text, {participants: "alice,bob,carol", topics: "caching,indexing"})
```

## Implementation Sequence

1. **qhorus-api** — `SummaryResult`, `SummaryUpdateContext` change, `SummaryUpdateHook` return type, `NoOpSummaryUpdateHook` update. Install to local Maven repo.
2. **qhorus runtime** — `ChannelSummary` annotations field, `ChannelSummaryEntity` annotations column + Flyway migration, `ChannelSummaryService.triggerUpdate()` round-trips `SummaryResult`, `QhorusMcpToolsBase.ChannelSummaryResult` exposes annotations. Install to local Maven repo.
3. **blocks.summarisation** — `SummaryMode` (move), `ContentSummariser<T>`, `VerbatimContentSummariser<T>`, `TieredContentSummariser<T>`, `ContentSummariserToSummariser<T>`
4. **blocks.summarisation.llm** — `LlmContentSummariser<T>`
5. **blocks.channel.summary** — `HeuristicMessageSummariser`, `ChannelSummariser`. Remove `HeuristicChannelSummariser`, `LlmChannelSummariser`.
6. **Tests** — unit tests for each new type, integration test for the full hook → tiered → LLM flow
7. **CLAUDE.md + ARC42STORIES** — documentation updates

## Type Inventory

| Type | Package | New/Changed/Removed | Dependencies |
|------|---------|---------------------|-------------|
| `SummaryResult` | qhorus-api spi | New | Pure Java |
| `SummaryUpdateHook` | qhorus-api spi | Changed (return type) | `SummaryResult` |
| `SummaryUpdateContext` | qhorus-api spi | Changed (previousResult) | `SummaryResult` |
| `NoOpSummaryUpdateHook` | qhorus runtime | Changed | `SummaryResult` |
| `ContentSummariser<T>` | blocks.summarisation | New | `SummaryResult` |
| `SummaryMode` | blocks.summarisation | Moved | Pure Java |
| `VerbatimContentSummariser<T>` | blocks.summarisation | New | Pure Java |
| `LlmContentSummariser<T>` | blocks.summarisation.llm | New | `AgentProvider`, Mutiny |
| `TieredContentSummariser<T>` | blocks.summarisation | New | `ContentSummariser` |
| `ContentSummariserToSummariser<T>` | blocks.summarisation | New | `ContentSummariser`, `Summariser` |
| `HeuristicMessageSummariser` | blocks.channel.summary | New | `Message`, `ContentSummariser` |
| `ChannelSummariser` | blocks.channel.summary | New | `SummaryUpdateHook`, `ContentSummariser` |
| `HeuristicChannelSummariser` | blocks.channel.summary | Removed | — |
| `LlmChannelSummariser` | blocks.channel.summary | Removed | — |
