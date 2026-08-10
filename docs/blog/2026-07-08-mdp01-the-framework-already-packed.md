---
layout: post
title: "The Framework That Was Already Packed"
date: 2026-07-08
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [summarisation, extraction, temporal-abstraction, quarkmind]
---

The summarisation framework in quarkmind was built with its destination address on the box. Every class sat in `io.casehub.blocks.summarisation` — the package name told you where it was going before anyone filed the extraction issue. Eight files, three test files, a clean API surface. The kind of extraction that should be mechanical.

It wasn't entirely mechanical. Reading the source with fresh eyes surfaced two design problems that quarkmind's single-consumer usage had hidden.

The first was `EventConsumer` — an interface that existed for the idea of a consumer contract but that nothing in the framework actually referenced. `EventStreamBus.subscribe()` takes a raw `Predicate<E>`. The interface was dead weight carried forward from an early design sketch. Removing it changed one line in one file.

The second was subtler. `SummarisationRunner.tick()` returned `void`. For quarkmind's synchronous game-loop summarisers — classify this batch of moments into a phase, classify these phases into an arc — that was fine. But the `Summariser` interface was explicitly designed for async LLM-backed summarisation via `CompletionStage`. When an LLM call fails, the buffer has already been drained. With a void return, the error vanishes and the events are gone. Changing the return type to `CompletionStage<Void>` makes the failure observable without adding recovery complexity. Existing sync callers don't notice — the stage is already complete.

The design review caught things I hadn't considered. The thread-safety model needed documenting: when an async summariser completes on a background thread, its `thenAccept` callback publishes to the output bus, which triggers downstream `collect()` calls on that background thread. `EventAccumulator` needed `synchronized` methods — not because quarkmind's single-threaded game loop required it, but because the framework's own wiring creates implicit cross-thread access that callers can't synchronize externally.

The review also pushed the examples beyond what I'd planned. Both the clinical and logistics pipelines now demonstrate fan-out topology — the L2 bus feeds both a `SummarisationRunner` (Pattern A) and a direct `EventAccumulator` (Pattern B) simultaneously. That's not a test-coverage exercise; it's the pattern quarkmind's commentary system actually uses, and it's worth showing to every future consumer.

What surprised me was how naturally the framework maps to academic work I'd read during the design phase. The Chain of Summarization paper (Ma et al., arXiv:2312.11865) independently arrived at the same architecture for LLM-based StarCraft II agents: single-frame compression, multi-frame aggregation, CoT reasoning. Shahar's clinical temporal abstraction taxonomy from the 1990s maps directly to our event levels. The framework isn't novel — it's a clean implementation of a pattern the literature has been converging on for thirty years.

The qhorus connection is the one that has me thinking. The chat UI spec for connectors describes a hierarchy — Space → Channel → Topic → Thread — that maps onto the summarisation levels with almost no friction. Correlation chains are episodes. Topics are phases. Channels carry the event stream. A `ChannelSummarisationBridge` that feeds qhorus events into `EventStreamBus` and publishes summaries back as channel messages would give any qhorus channel automatic temporal abstraction. That's filed as #40 and should develop alongside qhorus#328.
