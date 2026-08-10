---
layout: post
title: "The Last Mile of the Summarisation Pipeline"
date: 2026-07-25
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [summarisation, observation, architecture]
---

The summarisation framework has been growing for weeks — layered event compression, keyed accumulators, channel summary hooks, conversation folds with common ground and convergence detection. Each piece is independently useful, but what ties them together is a question none of them answered on their own: what does an LLM agent actually *see* when it's time to act?

That's the observation accumulator. It's the terminal consumer — the piece that sits at the end of the summarisation pipeline and renders everything into the prose that becomes the agent's context.

The layered pipeline compresses vertically: raw events at L1, classified episodes at L2, time-windowed phases at L3, narrative summaries at L4. Each `SummarisationRunner` stage feeds the next via `EventStreamBus`. The observation accumulator doesn't add another level — it subscribes at whatever level the agent cares about and renders what arrives. A character in a game subscribes at L1 and sees every door opening and line of dialogue. A supervisory agent subscribes at L3 and sees phase transitions. The hierarchical compression happened upstream; the observation accumulator takes what the pipeline already produced and makes it readable.

```java
// Low-level agent: observes raw events
l1Bus.subscribe(e -> true, characterAccumulator::collect);

// Supervisory agent: observes already-summarised phases
l3Bus.subscribe(e -> true, supervisorAccumulator::collect);
```

The tiered rendering adds horizontal compression on top. Three events get rendered verbatim — each one timestamped, full detail, one RAG chunk per event. Twelve events get grouped by category — movement, dialogue, interactions — one chunk per group with the group key in metadata. Fifty events go to a `Summariser<E, String>` for LLM compression, one chunk total. The tier boundary is just batch size against configurable thresholds. Vertical compression (pipeline levels) and horizontal compression (rendering tiers) are orthogonal — and `ObservationTier` mirrors `EventLevel` in shape to make that explicit. Same `record(String name, int ordinal)`, different dimension.

What makes this architectural layer interesting is how it composes with conversation folds. The observation accumulator answers *what happened* — the temporal stream of events since the agent last acted. Common ground analysis answers *what's been established* — which claims the group has acknowledged, which are still pending, which are disputed. Convergence detection answers *where the conversation is heading* — progressing, converging, deadlocked. These are three different facets of the same multi-agent situation, and they compose at the prompt level:

```java
// Three information sources, one prompt
prompt.append(observation.renderedText());      // what happened
prompt.append(renderer.render(state, ctx));      // conversation structure + epistemic status
// future: progress overlays (#62)
```

Each source is independently configured, independently testable, and produces a text fragment the agent can reason over. The observation accumulator doesn't know about conversation state. The conversation renderer doesn't know about event streams. They don't need to — they serve different cognitive needs, and the agent's LLM handles the synthesis.

The design question that anchored the whole thing was whether to reuse `EventAccumulator` internally. It looked natural — same thread-safe buffer, same atomic drain. But `EventAccumulator` carries `WindowPolicy` for tick-driven emission. Observation draining is demand-driven: the agent's turn decides when to drain, not a policy. The accumulator needed its own buffer without the emission logic — clean separation of the demand-driven terminal pattern from the tick-driven pipeline pattern.

The rendering SPI follows the same principle. `ObservationRenderer<E>` is a `@FunctionalInterface` returning `CompletionStage<ObservationResult>` — structurally parallel to `Summariser<IN, OUT>` but semantically different. `Summariser` is many-to-many (five vitals become two clinical events). `ObservationRenderer` is many-to-one (fifty events become one observation). `TieredObservationRenderer` is the standard implementation, but the SPI means a consumer with different rendering needs — a narrative renderer, a structured-data renderer, a domain-specific format — plugs in without touching the accumulator. The renderer is stateless, shareable across agent instances.

The channel summary hooks complete the picture from the other direction. `HeuristicChannelSummariser` and `LlmChannelSummariser` produce channel-level summaries for qhorus metadata — what the channel is *about*, updated as messages flow. The observation accumulator produces agent-level observations for LLM prompts — what *happened* to a specific agent. Channel summaries are persistent metadata. Agent observations are ephemeral prompt fragments. Different lifecycles, different consumers, same underlying event intelligence.

What I find satisfying about the current state of the summarisation package is that none of these pieces required the others to change. The observation sub-package composes with the existing primitives — `EventStreamBus` subscription, `Summariser` for the summarised tier, `LevelEvent` as the universal event wrapper — without modifying any of them. Seven new types, zero changes to existing code. That's the payoff of getting the foundational abstractions right in the first place.
