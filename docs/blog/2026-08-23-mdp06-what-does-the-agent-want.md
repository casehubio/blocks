---
title: "What Does the Agent Want? From Drives to Goals"
date: 2026-08-23
author: Mark Proctor
tags: [casehub, blocks, drive-architecture, intrinsic-motivation, social-cognition, observation, goal-generation]
issue: 149
entry_type: article
subtype: explanation
projects: [casehub-blocks]
---

# What Does the Agent Want? From Drives to Goals

Most LLM agents are reactive. They wait for input, generate a response, and go quiet. They have no internal state that persists between conversations, no sense of what matters to them, no motivational context beyond the current prompt. The social cognition stack in CaseHub Blocks changes this — and the Drive Architecture is where it starts to feel genuinely different.

## Drives Are Derived, Not Declared

The natural instinct when building agent motivation is to declare it: configure a curiosity level, set an affiliation score, tune autonomy. That gives you a knob, not a signal. The agent doesn't "want" anything — it performs a role someone configured.

Drives in the CaseHub stack work differently. They are *derived* from data the agent has already accumulated through its other cognitive systems. An agent that has interacted with three people, let two relationships decay, and has five fragmented memories it hasn't consolidated — that agent has a measurable affiliation drive and a measurable curiosity drive. Not because someone set a parameter, but because the data implies it.

Four axes, each reading from a different cognitive orchestrator:

| Drive | Source | Signal |
|-------|--------|--------|
| Curiosity | Memory hygiene | Knowledge gaps — fragmented, low-retention memories |
| Competence | Strategy learning | Engagement trend — declining interaction quality |
| Affiliation | User modelling | Relationship decay — neglected subjects |
| Autonomy | Mental modelling | Intention pressure — external intentions crowding the agent |

This is the compositor pattern. `DriveOrchestrator.tick()` pulls from these sources, modulates by personality and mood, and produces a `DriveProfile`. No raw signal accumulation — drives are a view over existing state.

## The Agent Sees What It Wants

A drive profile sitting in a cache is interesting architecturally but useless to the agent. The agent reasons through its LLM prompt — if drives aren't in the prompt, the agent doesn't know about them.

The observation pipeline in Blocks already renders cognitive state into the prompt: goals, recent activity, past experience, relationship notes. Each is a factory method on `CognitiveObservationSections` that returns an `ObservationSection`. Adding drives follows the same pattern:

```java
public static ObservationSection motivationalStateSection(DriveProfile profile) {
    var items = new ArrayList<String>();
    for (var axis : DriveAxis.values()) {
        var intensity = profile.drives().get(axis);
        if (intensity != null && intensity.intensity() >= 0.05) {
            String name = axis.name().charAt(0)
                        + axis.name().substring(1).toLowerCase();
            items.add(String.format("%s: %.1f — %s",
                    name, intensity.intensity(), intensity.trigger()));
        }
    }
    if (items.isEmpty()) {
        return ObservationSection.items(
                "Motivational State", "No active drives.", List.of());
    }
    return ObservationSection.items("Motivational State", null, items);
}
```

What the agent sees in its prompt:

```
== Motivational State ==
- Curiosity: 0.6 — 5 low-retention memories across 3 groups
- Affiliation: 0.8 — 2 of 3 relationships neglected
- Autonomy: 0.3 — 2 high-confidence intentions across 1 subject
```

The trigger provenance matters. "0.8" is a number. "2 of 3 relationships neglected" gives the agent something to reason about — it can choose to ask about someone it hasn't spoken to recently, or acknowledge the gap in conversation. The behaviour is emergent from the prompt context, not programmed.

The 0.05 threshold is worth noting. Mood modulation can nudge a raw zero to 0.02 — arousal amplifies everything slightly. Format that to one decimal place and you get `Curiosity: 0.0 — no hygiene data` in the prompt. A zero with a "no data" trigger is confusing signal. The threshold aligns what's displayed with what's meaningful.

## Optional Dependencies and Real Codebases

One of the four drive sources — `CuriosityDrive` — reads from `MemoryHygieneOrchestrator`, which has an 11-parameter constructor and no CDI annotations. The other three source orchestrators are CDI-managed. Making all four drive sources into CDI beans would mean CDI-enabling that 11-parameter constructor — a scope expansion for a different concern.

The solution is `Instance<T>` — CDI's optional injection. `DriveOrchestrator`'s `@Inject` constructor takes `Instance<MemoryHygieneOrchestrator>`. When the consumer provides it, curiosity works. When it doesn't, curiosity returns zero via a lambda fallback. The drive sources are never CDI beans — the orchestrator builds them internally.

This pattern — `Instance<T>` for optional injection with lambda fallback — is worth knowing for any CDI codebase where you compose across modules with different maturity levels. Not every dependency is ready to be managed; wrapping the absence is cleaner than expanding scope.

## From Self-Awareness to Autonomy

Right now, drives are visible but inert. The agent sees its motivational state and can reason about it — mentioning neglected relationships, showing curiosity about gaps in its knowledge — but it doesn't *act* on drives autonomously. That is Layer 2.

Layer 2 is `GoalProposer`: a component that reads `DriveProfile` and translates drive signals into concrete `GoapGoal` instances the engine's planner can execute. A high curiosity drive becomes a goal to explore unfamiliar topics. Declining competence becomes a goal to practise or seek feedback. The agent proposes goals during idle time without abandoning assigned work — self-generated goals sit below assigned goals in priority but escalate as drive intensity increases.

The architectural question Layer 2 must answer: who adjudicates when a self-generated goal conflicts with assigned work? The priority hierarchy — assigned goals first, high-intensity self-generated second, low-intensity third — is the starting point, but goal revision is the harder problem. When drives shift, self-generated goals should be abandoned, not persisted. An agent that stubbornly pursues a stale curiosity goal is worse than one with no goals at all.

Layer 1 gave the agent a mirror — it sees what it wants. Layer 2 gives it hands.
