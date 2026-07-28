# AffordanceRenderer — Grounded Observation Rendering for LLM Agents

**Issue:** casehubio/blocks#69
**Date:** 2026-07-28
**Status:** Design

## Problem

LLM agents cannot reliably map narrative reasoning to structured actions when
observations lack affordance grounding. The agent reasons correctly in natural
language but generates wrong action types because the observation shows display
names without three pieces of information:

1. **Identity** — what ID to use in the action target field
2. **Affordance** — which action type applies to this specific object
3. **Consequence** — what inventory items connect to what objects

All three links are necessary. Breaking any single one causes the LLM to default
to generic verbs (INTERACT, LOOK) despite reasoning correctly. Validated in
Wacky Manor Phase 2.5 — garden entry GE-20260728-f7ad43.

## Solution

Add an `AffordanceRenderer` to `casehub-blocks` that assembles structured
observations for LLM agents. The core value is per-entity affordance grounding
(the validated three-link chain). The framework value is typed section assembly
for complete observations.

### Two Concerns, One Class

1. **Core** — rendering entities with grounding chains (identity + affordance + consequence)
2. **Framework** — assembling complete observations from typed sections (entities, text, lists)

The section model supports three kinds of content matching what LLM observations
need: entity groups (grounded), text blocks (contextual prose), and item lists
(bulleted items). These are parallel to — not part of — the temporal observation
pipeline (`ObservationAccumulator` / `TieredObservationRenderer`). The consumer
concatenates structural output (from AffordanceRenderer) and temporal output
(from the accumulator).

### Consumer Levels — No Complexity Leakage

```java
// Level 1 — just entities. No sections, no sealed interface.
String text = renderer.renderEntities(entities);

// Level 2 — entity sections only
String text = renderer.renderObservation(List.of(
    ObservationSection.entities("Objects", "Nothing here.", objects),
    ObservationSection.entities("Exits", "No exits.", exits)));

// Level 3 — full observation with mixed section types
String text = renderer.renderObservation(List.of(
    ObservationSection.text("Location", locationDesc),
    ObservationSection.entities("Objects", "Nothing here.", objects),
    ObservationSection.items("Goals", "No goals.", goalStrings)));
```

Level 1 never touches `ObservationSection`. Level 2 uses only the `entities()`
factory. Level 3 uses all three factories. The sealed interface is hidden behind
factory methods.

## Package

`io.casehub.blocks.summarisation.observation.affordance`

Sub-package co-locates the section model (`ObservationSection` and variants) with
its consumer (`AffordanceRenderer`). The parent `observation` package is
exclusively the temporal pipeline (`ObservationAccumulator`, `TieredObservationRenderer`,
`ObservationResult`, etc.). Placing structural section types there would break
that cohesion. Both packages produce text for LLM agent prompts — they compose
at the prompt level, not at the type level.

## Types

### ObservableEntity

An entity visible to the agent with zero or more affordances.

```java
public record ObservableEntity(
        String id,
        String displayName,
        @Nullable String description,
        List<Affordance> affordances) {

    public ObservableEntity {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        affordances = List.copyOf(affordances);
    }

    public ObservableEntity(String id, String displayName, @Nullable String description) {
        this(id, displayName, description, List.of());
    }
}
```

### Affordance

An action available on an entity. Encodes the grounding chain: action type,
human-readable label, prerequisite item, and items this action accepts.

```java
public record Affordance(
        String actionType,
        @Nullable String label,
        @Nullable String requiredItem,
        List<String> acceptsItems) {

    public Affordance {
        if (actionType == null || actionType.isBlank()) throw new IllegalArgumentException("actionType required");
        acceptsItems = List.copyOf(acceptsItems);
    }

    public Affordance(String actionType, @Nullable String label) {
        this(actionType, label, null, List.of());
    }
}
```

### ObservationSection

Sealed interface with three variants. Factory methods are the consumer API.

```java
public sealed interface ObservationSection {

    String header();

    record EntityGroup(
            String header,
            @Nullable String emptyMessage,
            List<ObservableEntity> entities) implements ObservationSection {
        public EntityGroup {
            if (header == null || header.isBlank()) throw new IllegalArgumentException("header required");
            entities = List.copyOf(entities);
        }
    }

    record TextBlock(
            String header,
            String content) implements ObservationSection {
        public TextBlock {
            if (header == null || header.isBlank()) throw new IllegalArgumentException("header required");
            if (content == null || content.isBlank()) throw new IllegalArgumentException("content required");
        }
    }

    record ItemList(
            String header,
            @Nullable String emptyMessage,
            List<String> items) implements ObservationSection {
        public ItemList {
            if (header == null || header.isBlank()) throw new IllegalArgumentException("header required");
            items = List.copyOf(items);
        }
    }

    static EntityGroup entities(String header, @Nullable String emptyMessage,
                                List<ObservableEntity> entities) {
        return new EntityGroup(header, emptyMessage, entities);
    }

    static TextBlock text(String header, String content) {
        return new TextBlock(header, content);
    }

    static ItemList items(String header, @Nullable String emptyMessage,
                          List<String> items) {
        return new ItemList(header, emptyMessage, items);
    }
}
```

### ActionDescriptor

Describes an action type in the agent's vocabulary. Used by
`renderActionVocabulary` — typically placed in the system prompt, separate
from the per-turn observation.

```java
public record ActionDescriptor(
        String actionType,
        String description,
        @Nullable String parameterFormat) {

    public ActionDescriptor {
        if (actionType == null || actionType.isBlank()) throw new IllegalArgumentException("actionType required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description required");
    }
}
```

## AffordanceRenderer

Concrete class, instance-based. Configurable header formatter via
`withHeaderFormatter` (immutable-builder pattern, same as
`TieredObservationRenderer`). The function type differs:
`TieredObservationRenderer` takes `Function<ObservationContext, String>` (rich
temporal context → single observation header), while `AffordanceRenderer` takes
`Function<String, String>` (section title → formatted section header). Different
inputs reflect different concerns — temporal rendering has elapsed-time context,
structural rendering has section names.

```java
public class AffordanceRenderer {

    private final Function<String, String> headerFormatter;

    public AffordanceRenderer() {
        this(header -> "== " + header + " ==");
    }

    public AffordanceRenderer(Function<String, String> headerFormatter) {
        this.headerFormatter = headerFormatter;
    }

    public AffordanceRenderer withHeaderFormatter(Function<String, String> headerFormatter) {
        return new AffordanceRenderer(headerFormatter);
    }

    /** Core: render entities with grounding chains. Returns "" if entities is empty. */
    public String renderEntities(List<ObservableEntity> entities);

    /** Core with empty-state: returns emptyMessage if entities is empty. */
    public String renderEntities(List<ObservableEntity> entities, String emptyMessage);

    /** Framework: render a complete structured observation from typed sections. Returns "" if sections is empty or all sections are omitted. */
    public String renderObservation(List<ObservationSection> sections);

    /** Vocabulary: render action type descriptions under a consumer-provided header. Header is rendered as-is (not processed through headerFormatter). */
    public String renderActionVocabulary(String header, List<ActionDescriptor> actions);
}
```

## Rendering Format

### Entity Line

```
- DisplayName [id: ID]: Description text here. [AFFORDANCE1] [AFFORDANCE2]
```

Description is rendered as-is. The renderer adds `: ` before the description
and a space before the first affordance tag. No trailing punctuation is added
by the renderer — any period in the examples is part of the consumer-provided
description text.

If description is null: `- DisplayName [id: ID] [AFFORDANCE1]`

If no affordances: `- DisplayName [id: ID]: A dusty bottle.`

### Affordance Tag

| Fields present | Format |
|----------------|--------|
| actionType only | `[TAKE]` |
| actionType + label | `[TAKE to pick up]` |
| actionType + requiredItem | `[INTERACT, requires: fake-medal]` |
| actionType + label + requiredItem | `[INTERACT to examine, requires: fake-medal]` |
| actionType + acceptsItems | `[USE with: rat-poison, arsenic]` |
| actionType + label + acceptsItems | `[USE to apply, with: rat-poison]` |
| actionType + requiredItem + acceptsItems | `[USE, requires: key, with: rat-poison]` |
| actionType + label + requiredItem + acceptsItems | `[USE to apply, requires: key, with: rat-poison]` |

The format is compositional: after `actionType` (+ optional `label`), qualifiers
append in fixed order — `requires:` then `with:`. Both may co-occur because they
encode distinct grounding links: prerequisite (what you need) vs accepted inputs
(what you can apply).

### Section Rendering

```
== Visible Objects ==
- Rat Poison [id: poison]: A dusty bottle. [TAKE to pick up]
- Tea Service [id: tea-service]: A silver set. [USE with: rat-poison]

== Exits ==
- Kitchen [id: kitchen]: Warm kitchen with copper pots. [MOVE to enter]

== Your Inventory ==
You are carrying nothing.

== Your Goals ==
- [PRIMARY] Find the Doily Diamond
- [SECONDARY] Solve puzzles

== Current Location ==
Kitchen: A large room with copper pots hanging from the ceiling.
```

**EntityGroup:** entities rendered with grounding chains. Empty list renders
`emptyMessage` if non-null, omits section entirely if null.

**TextBlock:** content rendered as-is under the header.

**ItemList:** items rendered as `- item`. Empty list renders `emptyMessage` if
non-null, omits section entirely if null.

Blank line between sections. No trailing blank line after last section.

### Action Vocabulary

The consumer provides the header text. It is rendered as-is, not processed
through the `headerFormatter` — vocabulary rendering is a separate concern from
observation section rendering (typically placed in the system prompt, not in
per-turn observations).

```java
renderer.renderActionVocabulary("Available Actions:", actions)
```

```
Available Actions:
- MOVE <room-id>: Move to an adjacent room
- TAKE <object-id>: Pick up a portable object into your inventory
- USE <item-id> <target-id>: Use an inventory item on a target object
```

Pattern per action: `- ACTION_TYPE parameterFormat: description`

If parameterFormat is null: `- ACTION_TYPE: description`

## Relationship to Temporal Pipeline

AffordanceRenderer and `ObservationAccumulator`/`TieredObservationRenderer` are
**parallel producers**, not a pipeline. The consumer assembles both:

```
┌─────────────────────────┐     ┌──────────────────────────┐
│  AffordanceRenderer     │     │  ObservationAccumulator   │
│  (current world state)  │     │  (event stream over time) │
│                         │     │                           │
│  entities + sections    │     │  LevelEvent<E> buffer     │
│         ↓               │     │         ↓                 │
│  structural text        │     │  temporal text             │
└────────────┬────────────┘     └────────────┬──────────────┘
             │                               │
             └───────────┬───────────────────┘
                         ↓
                  consumer concatenates
                         ↓
                  complete observation
```

Returns `String`, not `ObservationResult`. The temporal pipeline's output types
carry metadata (`timeSinceLastDrain`, `eventCount`, `tier`) that don't apply
to structural rendering.

## Test Strategy

- **ObservableEntity:** null/blank id and displayName rejected; null description
  allowed; affordance list defensively copied
- **Affordance:** null/blank actionType rejected; all field combinations render
  correctly; acceptsItems defensively copied
- **ObservationSection:** factory methods produce correct types; null/blank
  header rejected on all variants; null/blank TextBlock content rejected;
  empty entity list + non-null emptyMessage renders message; empty list + null
  emptyMessage omits section; items and entities defensively copied
- **AffordanceRenderer.renderEntities:** single entity, multiple entities,
  entity with no affordances, entity with no description, entity with multiple
  affordances, empty list with emptyMessage returns emptyMessage, empty list
  without emptyMessage returns ""
- **AffordanceRenderer.renderObservation:** mixed section types, section
  ordering preserved, blank line separation, header format applied, empty
  sections list returns "", all sections omitted (empty entities + null
  emptyMessage on every section) returns ""
- **AffordanceRenderer.renderActionVocabulary:** consumer-provided header
  rendered as-is, multiple actions, null parameterFormat
- **Header formatter:** custom formatter applied to observation sections but
  NOT to vocabulary header
- **Wacky Manor integration test:** build the structural sections of a Wacky
  Manor observation (Location, Exits, Objects, Characters, Inventory, Goals)
  using AffordanceRenderer and verify the per-entity grounding chain output.
  Temporal sections (Recent Activity, Last Action Result) are out of scope —
  they come from the observation accumulator or are hand-assembled by the
  consumer

## Dependencies

No new dependencies. Pure Java records and a single concrete class. Uses
`@Nullable` from `org.jspecify` (already in blocks' compile dependencies).

## Consumer Impact

**Wacky Manor (Phase 2.6):** replaces hand-rolled `ObservationBuilder` with
AffordanceRenderer. The consumer maps `GameObject` to `ObservableEntity` and
passes sections. Temporal sections (Recent Activity, Last Action Result) come
from the observation accumulator or remain hand-assembled.

**Future consumers:** any LLM agent system where agents observe entities and
generate structured actions — clinical, IoT, game simulations, workflow agents.

**Action type consistency:** The grounding chain requires that `actionType`
strings in `Affordance` tags match those defined in `ActionDescriptor` entries.
This is a consumer responsibility — the framework uses `String` action types
for domain extensibility (game: MOVE, TAKE, USE; clinical: PRESCRIBE, DIAGNOSE;
IoT: ACTIVATE, CONFIGURE). Consumers should define action type constants in
their domain layer and use them consistently in both `ActionDescriptor`
definitions and `Affordance` construction, following the parameterised approach
from `ChannelMessageMeta` (each app defines its own vocabulary rather than the
framework prescribing one).

## Documentation Updates

- Update blocks ARC42STORIES.MD §5: add
  `io.casehub.blocks.summarisation.observation.affordance` sub-package entry
  under the existing `io.casehub.blocks.summarisation.observation` section,
  listing all 5 types (`ObservableEntity`, `Affordance`, `ObservationSection`,
  `ActionDescriptor`, `AffordanceRenderer`) with their roles
- Update blocks CLAUDE.md with affordance sub-package documentation
