---
layout: post
title: "The Observation That Doesn't Know Its World"
date: 2026-08-22
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [observation, spi, affordance, multi-agent, decoupling]
---

Wacky Manor's `ObservationBuilder` had been doing two things: assembling world perception (what's in the room, who's here, what you remember from the kitchen) and assembling cognitive state (your goals, your plans, your memories, your last action result). All twenty methods in one static utility, all taking `WorldState` and `CharacterState` directly.

This works until a second world wants observations. An SC2 agent or a Godot character has no `Room`, no `CharacterState`, no `WorldState`. But it has the same cognitive needs — goals, memories, reflections, activity history. Those are platform types (`AgentGoal`, `Memory`, `PartitionedDrain`), not manor types.

The split was a `WorldObservationProvider` SPI in the affordance package — a `@FunctionalInterface` with a single method: `List<ObservationSection> worldSections()`. Each world implements it. The manor provides `ManorWorldObservationProvider`, which captures `WorldState`, `CharacterState`, `PartitionedDrain`, and observer tags, then produces location, exits, objects, characters, remembered rooms, and perception-filtered sections. A separate `ManorExchangeObservationProvider` handles the simpler two-character dialogue path.

The cognitive side was the more interesting question. Five methods produce `ObservationSection` from pure platform types: `goalsSection(List<AgentGoal>)`, `recentActivitySection(PartitionedDrain)`, `pastExperienceSection(List<Memory>)`, `insightsSection(List<Memory>)`, `relationshipNotesSection(String, List<Memory>)`. These are formatters — 5-10 lines each, sorting goals by priority, filtering blank memory texts, prefixing relationship memories with "You recall:". Nothing deep. But they're the same for every agent, and they belong where the types they produce live.

Where is that? Three candidates.

**blocks** already depends on eidos-api and neocortex-memory-api. The cognitive formatters are `T → ObservationSection` factories — they sit alongside `ObservationSection.text()` and `ObservationSection.items()` as higher-level constructors that know about domain types. No new dependencies needed.

**quarkmind-core** has `WorldPerception`, `WorldBridge`, `AgencyLoop` — the agent cognition framework. Conceptually, "how agents represent cognitive state in prompts" fits here. But quarkmind-core is intentionally lean: engine-api and eidos-api only. Adding blocks and neocortex-memory-api would widen a focused core module for five utility methods. And quarkmind-core doesn't touch rendering at all today — it defines behavioral SPIs, not observation formatting.

**Stay in the manor** defeats the purpose. The whole point is that a second world shouldn't have to rewrite `goalsSection`.

blocks won. Factory methods belong with the type they produce. `CognitiveObservationSections` went into the affordance package alongside `WorldObservationProvider` and `ObservationSection`.

That left four methods that depend on `CharacterState` — a manor type blocks can't see. `inventorySection`, `currentThinkingSection`, `planSections`, `lastActionResultSection`. These stay in the manor's `ObservationBuilder`, which is now just an assembly point: call `provider.worldSections()`, append character state, append cognitive sections from the blocks utility. Eighty lines where there were three hundred and forty.

The three-way split follows the type boundary exactly: methods using `WorldState`/`Room`/`GameObject` → provider; methods using `AgentGoal`/`Memory`/`PartitionedDrain` → blocks; methods using `CharacterState` → manor. If `CharacterState` gets abstracted into a platform type someday, those four methods move too. But that's a different issue with different evidence.

One thing changed beyond the extraction: section ordering. The old code interleaved world and cognitive sections — `recentActivity` appeared before `remembered`, keen observations appeared after both. The new layout groups perception together (location, exits, objects, characters, remembered, keen/directed), then character state (inventory, thinking), then cognitive state (goals, plans, activity, memories, insights, last action). Cleaner for the LLM — "what's around you" before "what you're thinking."
