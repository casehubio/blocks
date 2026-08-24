---
entry_type: note
subtype: diary
series: mdp05
title: "The Agent That Remembers Its Story"
date: 2026-08-24
issue: 143
branch: issue-142-narrative-identity
tags: [narrative-identity, compositor, llm-synthesis, social-cognition]
---

# The Agent That Remembers Its Story

Layer 3 of the autonomous intelligence stack hit a design question I hadn't
fully thought through until we built it: what does it mean for an agent to
have a self-narrative, and how does that narrative change what the agent
*wants*?

The answer turned out to be a split. Not a design compromise — a genuine
architectural insight. LLM synthesis (turning raw reflections into episodes
and themes) is non-deterministic and expensive. Compositor tick (reading
cached narrative state and exposing it to the drive system) must be
deterministic, fast, and side-effect-free. Putting both in one component
breaks ADR-0001's guarantees. So NarrativeSynthesiser owns the LLM call
and writes to the store; NarrativeOrchestrator reads from the store and
caches. The consumer's scheduler controls ordering — synthesiser ticks
before orchestrator, orchestrator ticks before drives.

The more interesting design question was how synthesis works incrementally.
Episodes are additive — each synthesis adds new episodes from new
reflections, existing episodes persist. But themes are derived patterns
across ALL episodes. A new crisis episode should shift the "crisis-helper"
theme's salience globally. So themes are fully re-derived each synthesis:
the LLM sees all existing episode summaries plus new reflections and
produces the complete theme set. This hybrid — episodes incremental, themes
holistic — keeps the cost manageable (the LLM sees episode descriptions,
not full reflections) while ensuring theme salience reflects the full
narrative arc.

The adversarial review caught something I'd missed: NarrativeModulation's
additive composition across themes was unbounded. With `maxThemes=10` and
each theme contributing up to 1.0 per axis, the sum could reach 10x the
single-theme maximum — overwhelming DriveComposer regardless of the
`narrativeModulationStrength` scaling. The fix is clamping to [-1, 1] after
summation. Multiple themes reinforcing the same axis produce stronger
modulation than one, but the effect saturates. This mirrors the per-theme
weight bounds from DerivedTheme and preserves a clean semantic: each axis
ranges from "fully dampen" to "fully amplify."

The plan review surfaced a subtler problem. When reflections exceed the
per-synthesis cap, the watermark advances past unconsumed reflections —
they're permanently lost. The fix: when capping, set `synthesisedAt` to
the `generatedAt` of the last consumed reflection rather than the current
time. Unconsumed reflections remain findable in the next cycle. The same
review caught the degenerate response guard — an LLM returning empty
themes would silently wipe the agent's accumulated identity. Now that case
is treated as a synthesis failure: no state write, no watermark advance,
existing themes preserved for retry.

What strikes me about this work is how the narrative layer connects back
to everything below it. The agent's self-story isn't just a prompt
enrichment feature — it's a feedback loop. Themes modulate drive intensity.
Drives generate goals. Goals shape behaviour. Behaviour produces
experiences. Experiences become episodes. Episodes derive themes. The
cycle closes. An agent that frequently helps in crises develops a
"crisis-helper" theme, which amplifies its affiliation drive, which makes
it more likely to propose helping goals, which leads to more crisis
episodes. Identity becomes self-reinforcing.

The next piece is `CbrNarrativeStore` — persistence for the self-story.
Without it, the narrative lives only in memory. With it, the agent's
identity survives restarts. After that, NarrativeFeedback wires the
modulation into DriveComposer and adds governed priority escalation —
narrative context justifying a drive-sourced goal's promotion from
secondary to primary. The agent doesn't just want things; it wants them
*because of who it has become*.
