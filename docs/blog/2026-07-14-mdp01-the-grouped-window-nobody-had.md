---
layout: post
title: "The Grouped Window Nobody Had"
date: 2026-07-14
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [summarisation, qhorus, channel, keyed-accumulator]
---

## The Grouped Window Nobody Had

The summarisation framework shipped in #27 with two primitives: a flat accumulator that buffers events until a time or count threshold fires, and a runner that wires accumulator → summariser → bus. Every consumer since — quarkmind's game phases, the clinical example, the logistics pipeline — uses the same pattern: events arrive, fill a flat buffer, emit when the window triggers.

But qhorus correlation chains don't work like that. A COMMAND→STATUS→DONE chain is a bounded episode — it's complete when the terminal message arrives, not when a timer fires. Clinical encounter groupings have the same shape. So does AML transaction batch correlation. The framework had no way to express "group by key, emit when complete."

I wanted to scope this separately from the channel bridge — keyed accumulation is a framework concern, not a qhorus concern. Claude pushed back: the bridge is where correlation data enters the summarisation world. Design them apart and the bridge's extraction choices might discard the information the windowing needs. That was right. The holistic design made the key extractor a first-class parameter of the accumulator, and the adapter's null-return filtering the upstream gate for null keys.

`KeyedAccumulator<K, E>` groups events by a key function, emits each group independently when a completion predicate fires or a staleness timeout expires. The staleness uses clock-from-last-event semantics — the timer resets each time a new event arrives for a group. This prevents active chains with periodic STATUS updates from being force-emitted mid-flight. The flat `EventAccumulator` uses `shouldEmit()`/`drain()` as a pair; the keyed variant collapses that to a single `drain(long now)` because N groups can be in different states simultaneously.

The channel adapters themselves are thin. `ChannelEventAdapter` implements `MessageObserver`, calls an extractor function, publishes to a bus. `ChannelEventPublisher` subscribes to a bus, builds a `MessageDispatch`, dispatches best-effort. Neither knows about the other — a domain might use only one direction. Quarkmind's `MomentBroker` currently does Direction 2 manually in about 20 lines; the publisher replaces that with a constructor call.

The design review caught something I hadn't considered: when both directions share a channel, the published summaries re-enter as new events through `MessageObserverDispatcher`. Without filtering, that's an infinite loop — each summary produces a new event, which produces a new summary. The fix is a sender prefix convention: publishers use senders like `"summarisation.episodes"`, and the adapter's extractor filters anything starting with `"summarisation."`. One line in the extractor function, documented as the standard pattern for bidirectional pipelines.

The keyed accumulator is the piece I'm most interested to see used beyond channels. Encounter-bounded clinical episodes, transaction-batch AML correlation, session-scoped IoT grouping — they all want the same primitive. The channel bridge is one consumer of it.
