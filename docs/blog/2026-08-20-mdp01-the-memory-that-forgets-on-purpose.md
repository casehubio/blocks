---
title: The Memory That Forgets on Purpose
date: 2026-08-20
author: Mark Proctor
tags: [casehub, blocks, memory, cbr, consolidation, eviction]
issue: 120
epic: 126
entry_type: note
subtype: diary
---

# The Memory That Forgets on Purpose

Long-running agents accumulate memories the way old houses accumulate junk — everything gets kept because nothing seems worth the effort of deciding whether to throw it away. LUFY's finding that keeping less than 10% of conversation data actually *improves* user experience isn't surprising once you think about it. The value isn't in remembering everything; it's in remembering the right things.

The MemoryHygiene pattern is blocks' answer to this. It sits in `io.casehub.blocks.memory` — deliberately not under `agentic`, because memory lifecycle management isn't inherently about multi-agent interaction. A single-agent CBR system benefits from forgetting just as much as a swarm does. The placement mirrors `blocks.summarisation` — reusable infrastructure that agentic patterns compose but don't own.

## The pipeline that changed its mind

The original design had the pipeline running score → consolidate → evict. Score everything, merge the related ones, then throw out the garbage. Clean and sequential.

Claude's spec review caught the problem: consolidation creates new merged memories that have never been scored, while eviction would be using scores computed before the merge happened. The surviving memories are stale-scored, and the new ones escape review entirely. The fix was embarrassingly obvious in retrospect — evict first, then consolidate the survivors. Remove the garbage before deciding what to merge.

## Composite scoring vs. simple rules

The existing `CbrRetentionScheduler` uses `CbrRetentionPolicy` — hard cutoffs on age, count, and trust score. Any memory older than N days? Gone. More than M cases per type? Oldest go first. This works for housekeeping but can't express "this memory is old and low-trust but emotionally significant, so keep it."

We replaced that with a composite retention score — a weighted arithmetic mean of importance (arousal + surprise), recency (TemporalDecay factor), scope distance, and trust. All four signals already produce [0,1] factors, so combining them is just weights and arithmetic. One threshold to tune instead of three independent cutoffs. Agents that opt into MemoryHygiene disable the old scheduler for their domain to avoid conflicting decisions.

The importance scorers are heuristic by default — word-list sentiment for arousal, feature entropy for surprise. Good enough for basic filtering, but the SPI is there for consumers to wire LLM-backed implementations when psychological fidelity matters. The LUFY paper uses LLM scoring; we provide the hook without forcing the cost.

## Two-pass consolidation

The research cites MemGPT's "Sleeptime Agents" — using a stronger model during idle time to synthesise insights from accumulated memories. We split this into two passes that run at different times.

Pass 1 runs during the tick: `ContentSummariser` merges groups of similar memories into consolidated `FeatureVectorCbrCase` entries, superseding the sources. This is data reduction — 100 raw interaction records become 20 consolidated memories. The supersession mechanism gives us invalidate-not-delete semantics for free, with `SupersessionStatus.supersededAt` providing temporal context.

Pass 2 runs during idle maintenance: `ReflectionOrchestrator.reflect()` generates abstract insights — "this user raises security concerns after deployments" — that raw merging can't produce. These don't fit the CbrCase contract (no meaningful problem/solution/outcome structure), so they're stored as `ReflectionEntry` records through a separate `ReflectionStore` SPI. Keeping them out of CBR retrieval avoids polluting similarity queries with structurally mismatched entries.

## What's next

The queue has five patterns remaining in the autonomous agent epic. #121 is Mood — dynamic emotional state modulating memory retrieval and response. That one builds directly on what MemoryHygiene provides: mood-modulated retrieval needs memories worth retrieving, which means the hygiene pipeline needs to be running. The patterns are designed to compose, and the dependency chain is starting to show.
