---
title: The Mood That Colours Memory
date: 2026-08-21
author: Mark Proctor
tags: [casehub, blocks, mood, pad-model, emotion, retrieval, social-cognition]
issue: 121
epic: 126
entry_type: note
subtype: diary
---

# The Mood That Colours Memory

Personality is who you are. Mood is how you feel right now. The distinction matters because they operate on different timescales and affect different things. PersonalityEvolution nudges trait scores over weeks through accumulated interaction outcomes — slow, bounded, identity-level. Mood shifts in seconds from a single interaction and decays back to baseline in hours. And mood's primary effect isn't on what the agent says — it's on what the agent remembers.

The REMT paper (Frontiers in AI, 2026) makes the case: mood-modulated retrieval creates emotional continuity that static personality alone can't produce. A frustrated agent doesn't just sound frustrated — it recalls problem-resolution memories more readily, which shapes its approach to the conversation. A curious agent recalls exploratory memories. The mood colours the retrieval, and the retrieval shapes the response. The agent's emotional state is emergent from the feedback loop, not injected by a prompt instruction.

## PAD: the three axes that span emotional space

The Pleasure-Arousal-Dominance model maps any emotional state to three continuous dimensions, each [-1, 1]:

- **Pleasure** — the hedonic axis. Positive after a successful interaction, negative after conflict.
- **Arousal** — activation level. High when something novel or stressful happens, low during routine.
- **Dominance** — sense of control. High when the agent drives the conversation, low when overwhelmed.

Three dimensions are enough. Joy is (+P, +A, +D). Frustration is (-P, +A, -D). Boredom is (-P, -A, +D). The model is well-established in affective computing and avoids the combinatorial explosion of discrete emotion labels.

## The orchestrator that doesn't need an LLM

MoodOrchestrator is the simplest of the seven social cognition patterns. No LLM synthesis, no CBR cases, no per-subject state. The consumer provides PAD deltas via `MoodSignal` — the orchestrator applies them, clamps to max displacement from baseline, and applies exponential decay toward the personality-defined `MoodBaseline` on each tick.

```java
orchestrator.record(
    new MoodSignal.InteractionAppraisal(0.3, 0.1, 0.0, "positive feedback"),
    agentId, tenantId);
```

The delta model pushes appraisal logic to the consumer — consistent with how PersonalityEvolution pushes signal translation to `TraitPressureSource<E>`. The orchestrator doesn't need to know what "positive feedback" means in PAD terms; the consumer who understands their domain makes that mapping.

`MoodDecay` in neocortex handles the exponential decay: `value + (baseline - value) * (1 - e^(-t/τ))`. The time constant (default 4 hours) controls how quickly mood returns to baseline. An agent that received bad news at 9am is still slightly affected at lunch, back to normal by evening. The decay is continuous — every tick advances it — but bounded by max displacement, so even rapid successive events can't push mood beyond the configured range.

## What consumers do with it

`MoodModulatedRetrieval.reweight()` in neocortex takes the current `MoodState` and re-sorts a memory list by mood alignment. Memories stored with PAD attributes (via `MoodEvents.toMemoryInput()`) that are close to the current mood in PAD space get boosted; distant ones get suppressed. The `moodInfluence` parameter (default 0.3) controls how much mood affects retrieval versus other factors like recency and importance.

The consumer reads `orchestrator.currentMood(agentId, tenantId)` and passes it to the retrieval reweighter. The agent's behaviour changes not because the prompt says "you're frustrated" but because the memories it retrieves are the ones that match its current emotional state.
