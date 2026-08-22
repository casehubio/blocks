---
title: Seeing What You Want
date: 2026-08-22
author: Mark Proctor
tags: [casehub, blocks, drive-architecture, observation, social-cognition, prompt-enrichment]
issue: 149
entry_type: note
subtype: diary
---

# Seeing What You Want

Drive Architecture shipped yesterday — a compositor that derives motivational state from what the agent already knows. Four axes, four source orchestrators, personality and mood modulation. `tick()` without `record()`. The machinery exists. But nothing consumes it.

Today we wired it into the agent's perception. Not as autonomous behaviour — that's Layer 2 — but as self-awareness. The agent sees its own drives in its prompt, alongside goals, memories, and recent activity.

The rendering is deliberately simple. `CognitiveObservationSections` already has factory methods that convert cognitive state into prompt sections — goals from `AgentGoal`, recent activity from observation drains, past experience from memory. Adding `motivationalStateSection(DriveProfile)` follows the same pattern: iterate axes, format intensity and trigger provenance, skip anything below 0.05.

That threshold caught my attention during design review. The first spec said "filter axes with intensity == 0.0" — but mood modulation can turn a raw zero into 0.02. Format that to one decimal place and the agent sees `Curiosity: 0.0 — no hygiene data` in its prompt. A zero-valued drive with a "no data" trigger is confusing signal. The 0.05 threshold aligns what's displayed with what's meaningful.

The harder question was lifecycle wiring. DriveOrchestrator was a plain class — no CDI annotations. The source orchestrators it reads from are a mix: three are CDI-managed, one (`MemoryHygieneOrchestrator`) has an 11-parameter constructor with SPI types and no CDI annotations. Making all four drive sources into CDI beans would expand the scope into CDI-enabling MemoryHygieneOrchestrator — a separate concern.

The solution: DriveOrchestrator constructs its own drive sources internally. Its `@Inject` constructor takes the source orchestrators directly, with `Instance<MemoryHygieneOrchestrator>` for optional injection. When hygiene isn't available, curiosity defaults to zero via a lambda. The drive sources never need to be CDI beans — only the orchestrator and its composer are.

Per-drive configuration parameters — affiliation decay threshold, stale duration, autonomy confidence floor — moved into `DriveConfig` rather than being hardcoded in CDI constructors. The design review pushed for this: these are the values that need calibration ("what do drive intensities look like in a real agent?"), and baking them into constructors makes them unchangeable without a code change.

The tick itself is wired into `InnerLifeOrchestrator.doTick()` — at the very start, before any inner life logic runs. This is intentionally temporary. InnerLifeOrchestrator is the existing tick entry point, and drives must be computed before the agent decides whether to speak. But when Layer 2 arrives (GoalProposer, #136), drives will need to be computed independently of inner life. The coupling will be extracted then.

What the agent sees now:

```
== Motivational State ==
- Curiosity: 0.6 — 5 low-retention memories across 3 groups
- Affiliation: 0.8 — 2 of 3 relationships neglected
- Autonomy: 0.3 — 2 high-confidence intentions across 1 subject
```

It knows what it wants. It can reason about its own motivational state when generating responses — a form of self-awareness that enriches conversation without autonomous action. The drives are visible but inert. Layer 2 is where they become force.
