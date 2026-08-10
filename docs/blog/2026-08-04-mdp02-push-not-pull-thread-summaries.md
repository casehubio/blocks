---
layout: post
title: "Push, Not Pull — Thread Summaries That Write Themselves"
date: 2026-08-04
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [summarisation, qhorus, threads, architecture]
---

Channel summaries have a scheduler. Every 60 seconds, qhorus sweeps all channels, checks message thresholds, and fires a `SummaryUpdateHook`. The hook calls into blocks' `ContentSummariser<Message>`, which produces a structural or LLM-backed summary. Clean, simple, works.

Thread summaries can't work this way. Channels are finite and explicitly created — you can sweep them on a timer. Threads are unbounded. Every `correlationId` creates an implicit thread, and new ones appear constantly. A scheduler that sweeps all threads would scale as O(threads), which in a busy system means thousands of correlationId groups checked every tick, most of which haven't changed.

The alternative: push. The summarisation pipeline already knows when a thread completes — a DONE or FAILURE message arrives. The observer detects it, fetches the thread's messages, calls `ContentSummariser<Message>`, and writes the result to a `ThreadSummaryStore`. No scheduler, no sweep, no wasted work.

The design review caught something I'd missed. The original spec included HANDOFF as a trigger alongside DONE and FAILURE — it's terminal per `MessageType.isTerminal()`. But HANDOFF means the thread is being delegated to a new agent. The thread continues under a different participant. Summarising at HANDOFF produces a premature summary that is immediately stale when the new agent picks up. HANDOFF was excluded from the trigger set.

The same reasoning excludes RESPONSE. A simple QUERY-RESPONSE thread — the most common pattern in qhorus — never gets an automatic summary because RESPONSE isn't terminal. The thread might continue with follow-up queries. Consumers who want summaries for simple request-reply threads should close them with an explicit DONE. That's the correct signal: "this thread is complete."

The cross-repo split fell out naturally. qhorus owns storage — `ThreadSummary` record, `ThreadSummaryStore` SPI, in-memory and JPA implementations. blocks owns intelligence — a `ThreadSummaryObserver` that bridges `ContentSummariser<Message>` to the store. Without blocks on the classpath, thread summaries simply don't populate. The same `HeuristicMessageSummariser` that produces channel summaries produces thread summaries — no new summarisation logic, just a new trigger and storage path.

The concurrency guard is worth noting. Two terminal messages can race for the same `correlationId` — DONE and FAILURE arriving near-simultaneously. Without a guard, both trigger parallel summarisations that clobber each other. A `ConcurrentHashMap.newKeySet()` as an in-flight set handles this: `inFlight.add(key)` returns false for the second arrival, and the finally block removes the key when summarisation completes.

The observation pattern uses `@Observes(during = TransactionPhase.AFTER_SUCCESS)` to ensure the terminal message is committed before the observer queries for thread messages. The actual summarisation dispatches to a `ManagedExecutor` so it never blocks the message dispatch path — important when the `ContentSummariser` is LLM-backed and takes seconds rather than microseconds.

The interesting tension is between channel summaries (pull, timer-driven, qhorus-owned lifecycle) and thread summaries (push, event-driven, blocks-owned lifecycle). Same `ContentSummariser`, completely different triggering architecture. The thread count scaling problem forced the design toward push, and push turned out to be structurally cleaner: the component that knows when to summarise is the same component that does the summarising.
