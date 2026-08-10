---
layout: post
title: "The Format That Makes LLMs Act"
date: 2026-07-28
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [affordance, observation, llm-agents, grounding]
---

## The Format That Makes LLMs Act

The problem is simple to state and maddening to debug. An LLM agent reasons correctly in natural language — "I need to pick up the poison and use it on the tea service" — then generates `INTERACT` instead of `TAKE`. The narrative is right; the action is wrong. The world state doesn't change. The agent's next turn continues as if it succeeded.

I hit this in Wacky Manor's Phase 2.5 while validating autonomous characters. Five agents in a shared game world, each receiving an observation and responding with structured JSON actions. The Hooded Claw needed to TAKE poison and USE it on the tea service. He talked about it convincingly, then generated the wrong action type.

The obvious fix — describing what each action means in the response format — didn't work. `TAKE: pick up a portable object into your inventory` teaches the vocabulary but doesn't connect it to specific objects. The LLM knows what TAKE means in general; it doesn't know that this particular bottle of poison is TAKEable.

The fix is in the observation, not the output contract. Three links per entity, all inline: identity (`[id: poison]`), affordance (`[TAKE to pick up]`), and consequence (`[USE with: rat-poison]`). Break any one and the agent reverts to generic verbs. All three present, and the agent autonomously executes a multi-step plan — takes the poison, walks to the kitchen, uses it on the tea service.

### From hand-rolled to platform

Wacky Manor's `ObservationBuilder` had this baked into `appendVisibleObjects()` — a method that manually formatted each game object with its affordances. It worked, but it was domain-specific code solving a general problem. Any LLM agent system where agents observe entities and generate structured actions needs the same grounding chain.

AffordanceRenderer is the extraction. Five types in `io.casehub.blocks.summarisation.observation.affordance`:

- `Affordance` and `ObservableEntity` — the grounding chain data model
- `ObservationSection` — sealed interface with three variants: `EntityGroup` (entities with affordances), `TextBlock` (contextual prose), `ItemList` (bulleted items)
- `ActionDescriptor` — action vocabulary for system prompts
- `AffordanceRenderer` — renders entities with grounding chains, assembles complete observations from typed sections

The section model came from looking at what `ObservationBuilder` actually does. Eight sections, six structural (location, exits, objects, characters, inventory, goals) and two temporal (recent activity, last action result). The structural sections are all lists of things — some with affordances, some without. A sealed interface with three variants captures the pattern without forcing non-entity content into the entity model.

### Parallel producers, not a pipeline

One design decision worth noting: AffordanceRenderer sits alongside the existing `ObservationAccumulator` / `TieredObservationRenderer`, not inside it. The temporal pipeline handles "what happened since last turn" — event streams, windowed accumulation, tiered rendering by batch size. AffordanceRenderer handles "what can you do right now" — current entity state, snapshot-driven. The consumer concatenates both outputs. They share a prompt, not types.

### The adversarial review

Claude ran a three-round adversarial design review — two independent sessions debating the spec. Thirteen issues raised, eleven verified in the spec, two accepted after the implementor defended the original design. The sharpest finding: the affordance tag format table was missing the composite case where both `requiredItem` and `acceptsItems` are present. Both encode distinct grounding links (prerequisite vs accepted inputs) and can co-occur: `[USE to apply, requires: key, with: rat-poison]`. The spec now has eight rows covering all combinations.

The format is compositional — after the action type and optional label, qualifiers append in fixed order: `requires:` then `with:`. No special-case rendering logic, just concatenation with commas.
