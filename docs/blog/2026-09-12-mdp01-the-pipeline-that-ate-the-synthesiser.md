---
title: "The pipeline that ate the synthesiser"
date: 2026-09-12
author: mdp
entry_type: note
subtype: diary
tags: [summarisation, narrative-identity, refactoring, SPI-design]
projects: [casehub-blocks]
series: casehub-blocks
status: draft
---

# The pipeline that ate the synthesiser

`NarrativeSynthesiser` was doing too much. Gate logic, LLM invocation, episode building, pruning, state persistence, locking — all in one 440-line class. It worked, but when we built the decision narrative pipeline for #241, the same patterns showed up again: accumulate events, gate on some condition, summarise, persist state. The summarisation pipeline had the right abstractions for the common parts. The synthesiser just wasn't using them.

Issue #259 was the retrofit. Three new SPIs first, then decompose the synthesiser.

## The SPIs that were missing

The summarisation pipeline already had `Summariser`, `StatefulSummariser`, `ContentSummariser`, `EventAccumulator`, `WindowPolicy`, and two runner types. What it lacked were the extension points that identity narratives needed:

**`EmissionPolicy<IN, S>`** replaces `WindowPolicy` as the gating mechanism. The key difference: it receives the current summariser state, not just the buffered events and clock. The identity narrative gate needs to compare incoming reflections against existing episode text for novelty — that requires state. `WindowPolicy` couldn't express this because it knows nothing about what's been summarised. The policy is a `@FunctionalInterface` with `anyOf()` and `allOf()` composition, so you can build composite gates from simpler ones.

**`StateStore<S>`** adds opt-in persistent state. Both runners already managed state in a `ConcurrentHashMap`, but it was in-memory only — restart loses everything. `StateStore` adds write-through: reads check the map first, fall back to the store on cache miss, writes go to both. The identity narrative's `NarrativeMemory` wraps cleanly as a `StateStore<NarrativeState>`.

**`OutputProcessor<OUT, S>`** handles post-summarisation processing. The narrative synthesiser pruned episodes by age and themes by salience floor after every synthesis. That's not a summarisation concern — it's a capacity management concern. Moving it to `OutputProcessor` means any pipeline consumer can add their own post-processing without subclassing the runner.

All three live in `summarisation-api` with zero external dependencies. The runners got a builder API so consumers can opt in without a constructor explosion.

## Decomposing the synthesiser

With the SPIs in place, `NarrativeSynthesiser` split into six components:

- **`ReflectionEventAdapter`** — bridges the pull-based `ReflectionQueryStore` into a push-based `EventStreamBus`. Maintains a watermark per agent+tenant to avoid re-publishing.
- **`NarrativeEmissionPolicy`** — implements the three-part gate (count threshold, novelty check via token Jaccard distance, quiet period bypass). Same logic as before, now expressed as `EmissionPolicy<ReflectionEntry, NarrativeState>`.
- **`NarrativeContentSummariser`** — the thin `ContentSummariser` that keeps only the LLM synthesis: prompt assembly, invocation, JSON parsing, episode/theme building, and merge. No gate, no pruning, no persistence.
- **`NarrativeOutputProcessor`** — episode and theme pruning, extracted verbatim.
- **`CbrStateStore`** — adapter wrapping the existing `NarrativeMemory`.
- **`NarrativePipeline`** — the `@ApplicationScoped` factory that wires everything together, mirroring the `DecisionNarrativePipeline` pattern from #241.

The original `NarrativeSynthesiser` had zero production callers within blocks — `SocialAvatarCognition` uses `NarrativeOrchestrator`, the compositor that reads from `NarrativeStore`. Deleting it was clean.

## What this validates

The decision narrative pipeline (#241) proved the summarisation primitives could handle a new use case. This retrofit proves they can absorb an *existing* use case — one that predates the pipeline and was built independently. The same `SummarisationRunner` builder, the same `ContentSummariser.asSummariser()` bridge, the same `EventStreamBus` wiring.

The next consumer of these SPIs won't need to build a custom orchestrator. They'll write a `ContentSummariser`, pick an `EmissionPolicy`, optionally add a `StateStore` and `OutputProcessor`, and wire it through the builder. The identity narrative retrofit took the pipeline from "generic infrastructure" to "validated generic infrastructure."
