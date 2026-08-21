---
title: The Drift That Stays in Bounds
date: 2026-08-20
author: Mark Proctor
tags: [casehub, blocks, personality, jpaf, eidos, bounded-drift]
issue: 118
epic: 126
entry_type: note
subtype: diary
---

# The Drift That Stays in Bounds

LLMPTBench's NeurIPS 2025 workshop paper dropped an uncomfortable finding: agentic frameworks exhibit exaggerated personality shifts under negative events. A few bad interactions and your careful INTJ analyst is behaving like a cornered extrovert. The BFI-Adapt benchmark confirmed it from the other direction — LLM agents shift indiscriminately, both too much and in the wrong dimensions, compared to how humans actually change personality under event pressure.

The research is clear on what happens. The question is what to do about it.

## The machinery that already existed

Eidos has a complete JPAF (Jungian Personality Adaptation Framework) pipeline that most people don't realise is there. `DispositionSignalStore` accumulates activation counts per cognitive function. `DefaultDispositionHealth.probe()` computes effective weights from `baseWeight + activationCount × Δw` and detects when a function crosses an evolution threshold — auxiliary surpassing dominant, shadow surpassing primary, structural reorganisation. `DefaultDispositionEvolution.evaluate()` applies the four JPAF decision rules and normalises weights.

What's missing isn't the evaluation machinery. It's the signal translation layer that maps real interaction outcomes — behavioural compliance, relationship quality, goal achievement — into the activations that drive it. And the safety rails the literature says you need.

## Two-layer signal translation

The `TraitPressureSource<E>` SPI does the mapping. A positive behavioural signal activates the dominant function. A negative relationship event activates the compensating function. But which function terms to target depends on the agent's vocabulary.

Layer 1 is universal: read the agent's `dispositionProfile` terms directly. Big Five agents activate "openness" and "conscientiousness." DISC agents activate "dominance" and "influence." Jungian agents activate "Ti" and "Ne." Any agent with a populated profile participates — no vocabulary-specific code required.

Layer 2 is structural enrichment: for vocabularies that support `VocabularyTerm.opposite()`, the translator additionally infers shadow activations. A negative event on Ti also activates shadow Fe. This captures the compensatory dynamics that Jungian theory describes — functions don't operate in isolation. Agents without structural vocabulary skip Layer 2 and still evolve via Layer 1.

## Where dampening happens matters

The naive approach: dampen negative signals when recording them. Apply a 0.5 multiplier at write time and store already-attenuated counts. Simple, cheap, wrong.

Recording-time dampening permanently distorts the data. If you later discover your dampening factor should be 0.3 instead of 0.5, every historical signal is baked at the wrong weight. Retroactive recalibration is impossible because the raw signal is gone.

Probe-time dampening preserves both positive and negative activation counts separately via `ValenceCounts`. When `DefaultDispositionHealth.probe()` computes effective weight, it applies the dampening factor: `vc.effective(dampeningFactor)` returns `positive + round(negative × factor)`. Change the factor via preferences and the next probe reflects it immediately — no historical data loss. A/B testing different dampening factors across agent cohorts becomes a configuration change, not a data migration.

The cascaded attenuation with defaults works out to: one negative activation per tick contributes `1 × 0.5 (dampening) × 0.06 (reinforcementDelta) = 0.03` to effective weight, decaying by 20% each subsequent tick. Slow enough that you'd barely notice it happening, which is exactly the point.

## Two safety ceilings, one problem

Eidos already has `overReinforcementThreshold` — a per-function ceiling (default 0.50) that fires when one function becomes disproportionately strong. The orchestrator adds an L2 displacement ceiling — a global bound (default 0.15) on how far the overall profile can drift from its baseline.

These are distinct geometric properties. The per-function check catches one dimension dominating. The L2 check catches distributed drift across multiple dimensions that no single-function ceiling would detect — a little movement in every axis that adds up to a personality the designer didn't intend.

When the L2 ceiling is breached, the orchestrator sets a halt flag. Signal recording pauses. The next tick re-evaluates: if decay has brought displacement below the ceiling, recording resumes. If the JPAF pipeline triggers an evolution (dominant swap, auxiliary replacement), the profile resets to a new baseline with zero displacement. The halt flag is the pressure valve between "this agent is drifting" and "the JPAF rules haven't fired yet."

## Closing the loop

Every personality transition — Ti-dominant becoming Fe-dominant, say — gets recorded as a CBR case via `PersonalityTransitionSchema`. Old dominant, new dominant, trigger type, outcome (recorded asynchronously once the transition has had time to take effect). Future transitions can query similar past cases: "last time this agent shifted from introverted thinking to extraverted feeling under social pressure, how did that work out?" If badly — the dampening factor can be tightened. If well — the system has evidence that this kind of shift works for this agent.

Takata et al. showed that agents can develop genuine personality differentiation through free social interaction. The challenge isn't preventing change — it's preventing the wrong kind of change, at the wrong speed, in response to the wrong signals. Bounded drift with asymmetric dampening is the mechanism that makes personality evolution something an agent grows through rather than something that happens to it.
