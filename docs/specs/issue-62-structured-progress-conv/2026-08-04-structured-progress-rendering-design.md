# Structured Progress in Conversation Rendering

Integrate the work-api progress model into the conversation rendering pipeline
as a supplementary render-time input — the same pattern used for reactions,
common ground, and convergence.

**Issue:** casehubio/blocks#62
**Depends on:** casehubio/work#237 (progress model — shipped), blocks#49 (topic-aware conversation model — shipped)

---

## Context

### What exists

The conversation rendering pipeline already supports three supplementary
render-time inputs via `RenderContext`:

| Input | Source | Toggle |
|-------|--------|--------|
| Reactions | `Map<Long, List<ReactionGroup>>` from qhorus | (always rendered if present) |
| Common ground | `CommonGroundState` from `CommonGroundAnalyser` | `showEpistemicStatus` |
| Convergence | `ConvergenceSignal` from `ConvergenceAnalyser` | `showConvergenceSignal` |

Progress is the fourth. The pattern is established: caller produces a snapshot,
passes it on `RenderContext`, renderer displays it when the toggle is on and
data is non-null/non-empty.

### What work#237 delivered

Six modules in the work repo implementing `ProgressInstance` as a first-class
entity. The API types in `casehub-work-progress-api`:

| Type | What it is |
|------|-----------|
| `ProgressInstance` | Core entity: id, scope, shapeType, definition (JsonNode), state (JsonNode), status, parent/root pointers |
| `ProgressStatus` | PENDING, ACTIVE, COMPLETED, FAILED |
| `ProgressUpdatedEvent` | Event: previousState, currentState, changeType |
| `StepDefinition` | Step in a DAG: name, optional, dependsOn, condition |
| `StepStatus` | PENDING, ACTIVE, COMPLETED, SKIPPED, FAILED |
| `ProgressChangeType` | CREATED, STATE_UPDATED, CHILD_ATTACHED, COMPLETED, FAILED, REACTIVATED, ROLLED_BACK |

Three built-in shape types with validated state formats:

- **percentage** — `state: { "value": 63 }` (integer 0–100)
- **count** — `state: { "current": 3, "total": 7 }` (integers, current ≤ total)
- **step** — `definition: [StepDefinition...]`, `state: { "steps": { "name": { "status": "COMPLETED" }, ... } }`

Follow-up issues work#307 (arbitrary schema shapes), #308 (rollback controls),
#309 (visualisation modes) are extensions — not blockers.

---

## Design

### Principle: render-time input, not projection concern

Progress does not enter `ConversationState`, `ConversationProjection`, or
`ConversationFold`. It is a supplementary input on `RenderContext`, produced
by the caller from whatever source they have (REST API, SSE events,
direct `ProgressInstance` lookups). The renderer consumes a pre-grouped
snapshot and produces text.

### 1. New dependency

Add `casehub-work-progress-api` to blocks' `pom.xml` at provided scope
(same as other API dependencies — consumers bring the runtime).

### 2. ProgressRenderer SPI

```java
package io.casehub.blocks.conversation;

import io.casehub.work.progress.ProgressInstance;

@FunctionalInterface
public interface ProgressRenderer {
    String render(ProgressInstance progress);
}
```

Takes the full `ProgressInstance` — no intermediate snapshot type. The SPI
is in `blocks.conversation` alongside the other rendering types.

### 3. DefaultProgressRenderer

Built-in implementation. Switches on `progress.shapeType()`:

**Null safety:** If `state()` or `definition()` is null, falls back to
status-only rendering: `"Label: ACTIVE"`. All shape branches guard against
missing or malformed JsonNode fields with the same fallback — no exceptions
propagate from rendering.

**percentage** — `state.get("value")` → `"Label: 63%"`. Appends `" ✓"` when
status is COMPLETED. Appends `" ✗"` when status is FAILED.

**count** — `state.get("current")`, `state.get("total")`, optional
`definition.get("unit")` → `"Label: 3 of 7 sensors"`. Without unit:
`"Label: 3 of 7"`. Appends `" ✓"` when COMPLETED, `" ✗"` when FAILED.

**step** — Parses `definition` as `List<StepDefinition>` for step ordering.
If parsing fails, falls back to status-only rendering.
Reads `state.get("steps")` for per-step status. Renders as an arrow chain:
`"unpack ✓ → assembly ✓ → calibration ⏳ → testing ○"`. Status glyphs:

| StepStatus | Glyph |
|-----------|-------|
| COMPLETED | ✓ |
| ACTIVE | ⏳ |
| SKIPPED | ⊘ |
| FAILED | ✗ |
| PENDING | ○ |

Step shape uses step names as labels — no prefix label.

**fallback** (unknown shape) — `"Label: ACTIVE"` (raw status name).

**Label extraction:** `definition.get("label")` if present, otherwise
`progress.scopeId()`. Convention — not enforced by progress-api. Consumers
include a `"label"` key in their definition JsonNode.

### 4. RenderContext changes

Add `Map<String, List<ProgressInstance>> progress` field:

```java
public record RenderContext(
        Map<Long, List<ReactionGroup>> reactions,
        CommonGroundState commonGround,
        ConvergenceSignal convergence,
        Map<String, List<ProgressInstance>> progress) {

    public static final RenderContext EMPTY =
            new RenderContext(Map.of(), null, null, Map.of());

    public static RenderContext withReactions(Map<Long, List<ReactionGroup>> reactions) {
        return new RenderContext(reactions, null, null, Map.of());
    }

    public static RenderContext withProgress(Map<String, List<ProgressInstance>> progress) {
        return new RenderContext(Map.of(), null, null, progress);
    }
}
```

Map keys are topic names (same strings as `ConversationPoint.topic()`).
Keys must use `"general"` for the default topic — never null (matching
the `ConversationPoint.topic()` convention). Caller pre-groups by topic —
blocks does not know how to map scope to topic. List order within each
topic is caller-controlled and preserved by the renderer.
Empty map = no progress to render.

### 5. ConversationRendererConfig changes

Add `boolean showProgress` (default `false`). Builder gains `.showProgress(boolean)`.
Same pattern as the other four toggles.

### 6. ConversationRenderer changes

**Constructor** gains an optional `ProgressRenderer` parameter:

```java
public ConversationRenderer(ConversationRendererConfig config) {
    this(config, new DefaultProgressRenderer());
}

public ConversationRenderer(ConversationRendererConfig config,
                            ProgressRenderer progressRenderer) {
    this.config = config;
    this.progressRenderer = progressRenderer;
}
```

**renderByTopic()** — after topic header, before obligation chain:

```java
if (config.showProgress() && ctx.progress().containsKey(topicName)) {
    for (ProgressInstance pi : ctx.progress().get(topicName)) {
        sb.append(progressRenderer.render(pi)).append("\n");
    }
    sb.append("\n");
}
```

**renderFlat()** — all progress at the top under a "Progress" header:

```java
if (config.showProgress() && !ctx.progress().isEmpty()) {
    sb.append("**Progress**\n");
    for (var topicEntry : ctx.progress().entrySet()) {
        if (ctx.progress().size() > 1) {
            sb.append("_").append(topicEntry.getKey()).append(":_ ");
        }
        for (ProgressInstance pi : topicEntry.getValue()) {
            sb.append(progressRenderer.render(pi)).append("\n");
        }
    }
    sb.append("\n");
}
```

When multiple topics have progress, each group is prefixed with the topic
name in italics. Single-topic progress renders without a topic prefix.

### 7. Rendering position in topic section

```
## review                                  ← topic header (existing)

Code quality: 63%                          ← progress overlay (NEW)
Coverage: 3 of 7 sensors                   ← one line per ProgressInstance
setup ✓ → lint ✓ → test ⏳ → deploy ○      ← step shape renders inline

COMMAND → STATUS → DONE ✓                  ← obligation chain (existing)
3 established, 1 pending, 0 disputed       ← epistemic summary (existing)

## ⬜ [point-1] ...                         ← points (existing)
```

Progress goes immediately after the topic header — it is the highest-level
"where are we" for the topic.

### 8. What doesn't change

- `ConversationState` — no new fields
- `ConversationProjection` — no progress parsing
- `ConversationFold` — no progress folding
- `ConversationProtocol` — no new metadata keys
- `ChannelAgentDispatcher` — no progress concerns

### 9. Migration note

Adding the `progress` field to `RenderContext` (a record) changes its
canonical constructor. All existing call sites — `EMPTY`, `withReactions()`,
and any direct constructor calls in tests — must be updated to include
the new `Map.of()` parameter. This is a mechanical change. The updated
`EMPTY` and `withReactions()` factory methods handle it for most callers.

---

## Test Strategy

Plain JUnit 5 + Mockito. No CDI, no Quarkus runtime.

### DefaultProgressRendererTest

| Test | Input | Expected output |
|------|-------|----------------|
| percentage with label | `shapeType="percentage"`, `definition={"label":"Calibration"}`, `state={"value":63}` | `"Calibration: 63%"` |
| percentage completed | `state={"value":100}`, `status=COMPLETED` | `"Calibration: 100% ✓"` |
| percentage failed | `state={"value":63}`, `status=FAILED` | `"Calibration: 63% ✗"` |
| count with unit | `shapeType="count"`, `definition={"label":"Coverage","unit":"sensors"}`, `state={"current":3,"total":7}` | `"Coverage: 3 of 7 sensors"` |
| count without unit | No `unit` in definition | `"Coverage: 3 of 7"` |
| step shape | 4-step definition, mixed statuses | `"unpack ✓ → assembly ✓ → calibration ⏳ → testing ○"` |
| step with skipped | Step has SKIPPED status | `"... → optional ⊘ → ..."` |
| step with failed | Step has FAILED status | `"... → wiring ✗ → ..."` |
| unknown shape | `shapeType="custom"` | `"Label: ACTIVE"` (fallback) |
| null state | `state=null` | `"Label: ACTIVE"` (fallback) |
| null definition | `definition=null`, `scopeId="cal-001"` | Uses "cal-001" as label, renders via fallback |
| malformed state | `shapeType="percentage"`, `state={"wrong":true}` | `"Label: ACTIVE"` (fallback) |
| label from definition | `definition={"label":"Calibration"}` | Uses "Calibration" |
| label fallback to scopeId | No label in definition, `scopeId="cal-001"` | Uses "cal-001" |

### ConversationRendererTest (progress integration)

| Test | What it proves |
|------|---------------|
| progress in topic view | Progress entries render between topic header and obligation chain |
| progress in flat view — single topic | Progress renders under "Progress" header, no topic prefix |
| progress in flat view — multi topic | Each topic group prefixed with italic topic name |
| showProgress=false | Progress on RenderContext but toggle off → no progress in output |
| empty progress map | No crash, no output |
| custom ProgressRenderer | Inject custom impl, verify it is called and output appears |
| multiple topics with progress | Each topic section shows only its own progress entries |
| topic with no progress | Topic renders normally — no empty progress section |
| ordering preserved | Progress entries render in list insertion order |

### RenderContextTest

| Test | What it proves |
|------|---------------|
| EMPTY constant | Progress map is empty, not null |
| withProgress factory | Creates context with progress, other fields defaulted |
| withReactions factory | Progress map is empty (backward compatible) |

---

## Impact on consumers

All changes are additive. `showProgress` defaults to `false`. Existing
`RenderContext.EMPTY` and `withReactions()` continue to work — progress
map defaults to empty.

| Consumer | Impact |
|----------|--------|
| drafthouse | Can opt in via `showProgress(true)` + providing progress on RenderContext |
| devtown | Same — opt-in |
| clinical, AML | Use oversight blocks, not conversation — unaffected |

---

## Dependencies

**New compile dependency:**
- `casehub-work-progress-api` (provided scope)

**Existing (unchanged):**
- `casehub-qhorus-api` — `ReactionGroup`, `MessageType`
- blocks' own conversation package — all changes are internal
