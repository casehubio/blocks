---
entry_type: note
subtype: diary
title: "The summary that knows what it is"
date: 2026-07-29
author: mdp
project: casehub-blocks
tags: [summarisation, channel-summary, architecture, qhorus, content-summariser]
status: draft
---

65% of enterprise AI agent failures are attributed to context drift — the agent loses track of where the conversation is, what's been decided, and what's still open. The longer a channel runs, the worse it gets. An agent joining a hundred-message channel has to scan the full history to understand "where are we?" — filling its context window with raw messages when what it actually needs is three paragraphs of state.

That's the problem channel summaries solve. Qhorus added a summary slot per channel — metadata *about* the conversation, not a message *in* it. A hook fires when the message count crosses a threshold, receives the recent batch, and returns the updated summary text. Agents call `get_channel_summary` before deciding whether to project the full history. Cheap context, on demand.

The original implementation had two standalone classes: a heuristic summariser that extracted participants, topics, and time spans (zero LLM cost, structural-only), and an LLM-powered one that asked a model to rewrite the summary incorporating new information. CDI selected one or the other at deployment time. Both worked. Neither composed.

The problem wasn't that they were bad — it was that they were islands. The heuristic logic couldn't be reused in a pipeline context. The LLM logic couldn't be mixed with the heuristic for adaptive batch handling. And neither could produce structured output alongside the text. A three-message batch got the same treatment as a fifty-message batch, because CDI chose the strategy at deployment, not at invocation.

## What changed

The blocks summarisation module already had a temporal abstraction framework — `EventStreamBus`, `SummarisationRunner`, tiered observation rendering. But the channel summary hooks were built separately and never connected to it. An architectural audit surfaced three things: the summarisation algorithms were locked inside hook implementations, the qhorus SPI returned `String` (preventing structured output), and the tiered dispatch pattern from the observation module was absent from channel summaries.

We re-architected across three layers. The dependency chain matters here — qhorus is upstream of blocks, so the SPI lives in qhorus-api and the intelligence lives in blocks.

**Layer 1: qhorus-api.** `SummaryUpdateHook` now returns `SummaryResult` — text plus a `Map<String, String>` of annotations. Qhorus stores annotations alongside the text and round-trips them: the next invocation receives the previous result's annotations, enabling cross-invocation state. The annotations are opaque to qhorus — blocks puts whatever structured metadata it wants in there (tier used, participant lists, topic accumulation). Qhorus stores and serves it without needing to understand it.

**Layer 2: blocks.summarisation.** `ContentSummariser<T>` is a new `@FunctionalInterface` — `CompletionStage<SummaryResult> summarise(List<T> items, SummaryResult previous)`. It takes raw items (not `LevelEvent<T>` — decoupled from the pipeline event model) and an optional previous result for running-summary lifecycle. Three implementations:

```java
// Verbatim — list each item. For small batches.
var verbatim = new VerbatimContentSummariser<>(msg -> "[" + msg.sender() + "] " + msg.content());

// LLM — ask a model to synthesise. EDIT rewrites, APPEND adds a section.
var llm = new LlmContentSummariser<>(agentProvider, renderer, SummaryMode.EDIT);

// Tiered — adapt strategy to batch size.
var tiered = new TieredContentSummariser<>(verbatim, heuristic, llm, 5, 20);
```

`TieredContentSummariser` dispatches based on batch size: three messages get verbatim listing (cheap, detailed), fifteen get heuristic extraction (participants, topics, period), fifty get LLM synthesis (the expensive path, used only when the batch is large enough to justify it). The thresholds are constructor arguments — the consumer chooses.

**Layer 3: blocks.channel.summary.** `HeuristicMessageSummariser` is a `ContentSummariser<Message>` that extracts structural metadata from qhorus messages and merges annotations across invocations — participant lists grow, topic lists accumulate, nothing is dropped. `ChannelSummariser` is a thin adapter implementing `SummaryUpdateHook` that delegates to whatever `ContentSummariser<Message>` CDI provides.

## Why the separation matters

The same `ContentSummariser<Message>` works in two contexts without modification. In the hook path, qhorus fires the trigger and `ChannelSummariser` delegates. In a pipeline path, `ContentSummariserToSummariser` wraps the same algorithm as a `Summariser<T, String>` for use in a `SummarisationRunner`. One algorithm, two orchestration patterns.

The annotation propagation contract is what makes tiered dispatch actually work for running summaries. Each implementation starts from `previous.annotations()` as its base map and overlays only the keys it owns. When a heuristic invocation follows a verbatim one in a tiered sequence, the verbatim tier's annotations survive. Domain-specific keys set by other code survive too. Without this contract, a tier transition would destroy accumulated state — and that's exactly the bug the design review caught on round two.

I'm interested to see how domain repos use the tiered option. The CDI wiring is intentionally simple — blocks provides the `@DefaultBean` heuristic, domain repos that want tiered dispatch produce a `ContentSummariser<Message>` bean that composes the pieces they want. The LLM tier is generic on `T`, so non-channel contexts (case activity summaries, agent observation digests) can reuse it directly.
