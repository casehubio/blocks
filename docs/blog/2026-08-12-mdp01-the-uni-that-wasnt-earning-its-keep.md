---
title: "The Uni that wasn't earning its keep"
date: 2026-08-12
author: mdp
entry_type: note
subtype: diary
tags: [virtual-threads, mutiny, reactive, event-driven, choreography]
projects: [casehub-blocks]
status: draft
---

I came in to add event-bus integration to ChoreographedDriver — the issue had been open since the engine integration spec, parked as a deferred concern. The driver had a `WaitingForEvent` state transition between iterations, but it was cosmetic. No actual waiting. The loop ran continuously, identical to OrchestratedDriver.

The design question seemed straightforward: how should ChoreographedDriver wait for external events (channel messages, work item completions, timer ticks) between iterations? The original spec said "genuinely reactive — Uni chaining, not blocking." That would mean converting the five-phase loop from blocking `.await().indefinitely()` calls into a Mutiny Uni chain, where each SPI call transforms into the next without pinning a thread.

I started down that path. Then stopped.

The platform has moved to virtual threads. Blocking a virtual thread costs nothing — the JVM handles the scheduling. The entire motivation for reactive Uni chains on internal code is thread efficiency, and virtual threads eliminate that argument. The question flipped: instead of "how do we make this reactive," it became "where does Uni still earn its place?"

The answer was smaller than expected. `AgentInvoker.invoke()` keeps Uni because `Uni.join().all()` enables concurrent multi-agent dispatch — genuine compositional value. `ExecutionDriver.execute()` keeps Uni because callers chain on the result — it's an API boundary. But `RoutingStrategy.route()`? `ActivationRule.shouldActivate()`? Every implementation wraps synchronous logic in `Uni.createFrom().item()`, and every consumer immediately calls `.await().indefinitely()`. The Uni is ceremony.

So we audited the entire agentic package. Eight SPI interfaces, their implementations, the pattern builders, the conversation orchestrator's termination chain. Eighty-two files touched, 143 lines removed. The before/after in `executeIteration()` tells the story:

```java
// Before: five lines of ceremony per SPI call
var decision = model.routing().route(routingCtx).await().indefinitely();
var activated = model.activation().shouldActivate(activationCtx).await().indefinitely();

// After: direct calls
var decision = model.routing().route(routingCtx);
var activated = model.activation().shouldActivate(activationCtx);
```

`CompositeTermination` went from a Uni flatMap chain to a for loop. `HeuristicDecomposition`'s backtracking method went from recursive `recoverWithUni` error handling to a try-catch in a loop. Every change made the code simpler and more obvious.

With the reactive Uni question resolved, the ChoreographedDriver design became clear. No Uni chains for the internal loop. A `BlockingQueue<DriverEvent>` fed by `EventSource` subscriptions. A composable `EventConcurrencyPolicy` that controls how queued events are consumed — serialize by default (actor-mailbox semantics), with coalesce and coalesce-by-source for high-frequency scenarios. The driver blocks on `queue.take()` between iterations, which is free on a virtual thread. When an event arrives, it wakes up, runs the five-phase loop, and goes back to sleep.

The two-mode design preserves backward compatibility: no EventSource means legacy continuous-loop behaviour; EventSource present activates event-driven mode. Cancellation posts a poison-pill event to the queue rather than interrupting the thread — simpler and avoids the platform-thread pinning concerns that come with `Thread.interrupt()` on virtual threads.

The decision that matters here isn't the event-bus design — that's mechanical once the approach is clear. It's the Uni audit. Virtual threads don't just make blocking cheap; they change which abstractions earn their place. A reactive wrapper that existed for thread efficiency becomes noise when threads are free. The question isn't "should we be reactive" — it's "where does reactivity add design value beyond thread management?" For blocks, the answer was: API boundaries and concurrent composition. Everywhere else, direct calls.
