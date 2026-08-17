---
layout: post
title: "The Callback That Isn't a Round"
date: 2026-08-17
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [conversation, orchestration, spi, design]
---

The issue asked for a `RoundListener` — a callback on `ConversationOrchestrator` that fires per round so consumers like fsitrading can push convergence updates via WebSocket. Simple enough, except the orchestrator's termination check doesn't fire at round boundaries. It fires after each individual agent response.

This matters because "round" is poorly defined in a queue-based conversation loop. A message enters the queue, the turn policy selects responders, each responder's output gets folded into the projection and added back to the queue. Termination is evaluated after every successful dispatch — not at some boundary where all responders for a given message have spoken. Calling this a round callback would mislead consumers about the granularity they're receiving.

The fix was naming: `ConversationListener` with `onDispatch`, not `RoundListener` with `onRound`. Per-dispatch is also the right default granularity — a consumer wanting round-level updates can aggregate externally, but you can't recover per-dispatch detail from a round-level callback.

The interface shape was an easy call. The existing `ExecutionEventListener` in the agentic package is a multi-event interface with ten default methods covering routing, activation, aggregation, termination, and state transitions. That pattern fits execution models where many lifecycle events need observation. Here there's exactly one event to observe. A `@FunctionalInterface` is the simplest thing that works — lambda-friendly, and adding a second SPI later if other conversation events emerge is cheap and non-breaking.

The signature landed at four parameters: `ConversationState`, `TerminationDecision`, `int dispatchCount`, and `Duration elapsed`. The first three were in the original issue's suggestion. `elapsed` was the one addition — already computed at the call site for `TerminationContext`, and useful for progress reporting without forcing consumers to track their own timers. Passing the full `TerminationContext` would have been the zero-cost alternative, but that type carries `List<AgentResult>` — heavy context the listener doesn't need per-dispatch, and coupling the conversation SPI to an agentic-layer type felt wrong.

The wiring is a nullable field with two constructors: the original 9-arg stays for backward compatibility, delegating to a new 10-arg with the listener as the last parameter. One call site, one null check. fsitrading can now wire convergence broadcasting as a one-line lambda.
