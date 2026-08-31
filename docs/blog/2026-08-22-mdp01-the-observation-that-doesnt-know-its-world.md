---
layout: post
title: "The Observation That Doesn't Know Its World"
date: 2026-08-22
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [observation, spi, affordance, multi-agent, decoupling, architecture]
---

# The Observation That Doesn't Know Its World

Every LLM agent needs a prompt that captures two things: what the agent perceives and what it's thinking. Build them as one function and it works. Build them as a composition and every agent you ever write can use the same cognitive stack.

The observation architecture in casehub-blocks splits prompt assembly into three layers: a sealed type system that the renderer sees, a world-specific SPI that each application implements, and a cognitive utility that every agent shares. The interesting design question was where to draw those boundaries.

## The type system — three shapes, one renderer

`ObservationSection` is a sealed interface with three permitted variants: `EntityGroup` (things with affordances — characters, objects, exits), `TextBlock` (free-form prose — location description, recent activity), and `ItemList` (enumerable items — goals, memories, inventory). Every piece of an agent's observation reduces to one of these three shapes.

The renderer — `AffordanceRenderer` — doesn't know about rooms or goals or memories. It knows how to render an `EntityGroup` (show each entity with its affordances), a `TextBlock` (print the content under a header), and an `ItemList` (bullet each item). That's the whole contract. A goals section and a location section look identical to the renderer. The domain knowledge lives elsewhere.

The `EntityGroup` variant carries the affordance model: each `ObservableEntity` has a list of `Affordance` records — action type, label, required items, accepted items. When the renderer produces text, the agent sees not just "Kitchen Cabinet" but `Kitchen Cabinet [INTERACT, requires: brass-key]`. The action vocabulary is embedded in the perception, not passed as a separate tool list. The LLM gets what it can do in the same context as what it sees.

## The world boundary — one method

`WorldObservationProvider` is a `@FunctionalInterface`:

```java
List<ObservationSection> worldSections();
```

Each world implements it. The manor's implementation captures `WorldState`, `CharacterState`, the event drain, and observer tags, then produces location, exits, objects, characters, remembered rooms, and perception-filtered sections. The exchange path (two-character dialogue) has its own minimal provider — location, others present, dialogue text.

An SC2 agent would return units, terrain, fog of war. A Godot character might return nearby nodes and navigation mesh data. The observation architecture doesn't care. It receives sections and renders them.

## The cognitive side — the part every agent shares

Five factory methods in `CognitiveObservationSections` produce `ObservationSection` from platform types that every CaseHub agent already has access to: `AgentGoal`, `Memory`, `PartitionedDrain`. Goals get sorted by priority. Memory texts get blank-filtered. Relationship memories get prefixed with "You recall:". These are simple formatters — ten lines each — but they're the same for every agent, and rewriting them per world is wasted effort.

The split follows the type boundary: if a method's parameters are all platform types (`AgentGoal`, `Memory`, `PartitionedDrain`), it's cognitive — it goes in blocks. If it needs world-specific types (`WorldState`, `Room`, `GameObject`), it's perception — it stays in the world's provider. Four methods that depend on `CharacterState` (inventory, current thinking, plans, last action result) stay in the manor's assembler. If `CharacterState` gets abstracted into a platform type someday, those move too. But that's a different decision with different evidence.

The architecture proved its boundary when motivational state and self-narrative sections were added later. `DriveProfile` and `NarrativeState` are platform types — they got two new factory methods in `CognitiveObservationSections`. No interface change, no provider change, no renderer change. Two methods, immediately available to every agent.

## The assembly — thin by design

The builder is now an orchestrator: call `provider.worldSections()`, append character state, append cognitive sections from the blocks utility, render. Eighty lines where there were three hundred and forty. The section ordering changed from interleaved (world, then cognitive, then world again) to grouped — all perception first, then all internal state. Cleaner for the LLM: "what's around you" before "what you're thinking."

The composition has a property worth noting: the world provider controls world-section ordering, the builder controls the overall grouping, and the renderer controls the text format. Each layer owns one decision. Adding a new section — a trust score, a social norm assessment — means writing one factory method and one line in the builder. The architecture absorbs it.
