---
title: "The observer that didn't change the driver"
date: 2026-08-14
author: mdp
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [agentic, channel-observation, composition, event-source, termination]
status: draft
---

The execution drivers have always been blind. OrchestratedDriver and
ChoreographedDriver run the five-phase loop — route, activate, dispatch,
aggregate, terminate — but they only see what comes back from agent
dispatch. If agents are talking to each other through a qhorus channel,
the supervisor has no idea what they're saying. It sees results, not
the conversation.

ConversationOrchestrator solved this by being its own loop. It maintains
ConversationState explicitly, folds every message through a projection,
and passes the projected state directly to its termination condition.
But it's not an execution driver — it doesn't compose with the five SPIs
or the pattern builders. The generic drivers needed the same capability.

The obvious move was to enrich TerminationContext — add a projection
state field so the driver delivers channel observations alongside the
execution context T. Three patterns from the literature converge on
the same principle (blackboard architecture, event sourcing projections,
the ActiveGraph paper's "state is a fold over an immutable event log"),
and all of them suggest the observation should be a derived,
purpose-shaped view of a shared event stream. That's exactly what
ChannelProjection already is: `identity()` + `apply(S, MessageView)`.

But enriching the context means changing TerminationContext,
AggregationContext, and AbstractExecutionDriver. Every existing
consumer sees the change. And the type parameter problem is real — the
projection state S is a different type from the driver's context T.
You'd need a second type parameter or an `Object` escape hatch. Neither
is clean.

What actually worked: ChannelObserver implements EventSource. When a
channel message arrives, the observer folds it through the projection
and posts a DriverEvent. The ChoreographedDriver wakes up. The
termination condition reads from the observer via closure. No SPI
changes. No driver changes. The observer IS the event source — it
both updates the projection and wakes the driver in one operation.

The event delivery ordering is what makes the side-channel safe. The
observer calls `updateAndGet()` before `sink.accept()`. By the time the
driver wakes and evaluates termination, the projected state already
reflects the message that triggered the wake. There's no window where
the driver could read stale state.

The design review caught three things worth fixing: `subscribe()` needed
a guard against concurrent callers (an `@ApplicationScoped` CDI bean
persists across executions), projection exceptions needed to be caught
rather than letting `updateAndGet` propagate them, and the `or()`/`and()`
combinators on TerminationCondition needed priority ordering — without
it, a Complete from one condition silently swallows an Escalate from
another.

The priority hierarchy (Escalate > Failed > Complete > Continue) is
small but consequential. It means you can compose termination conditions
freely — `observer.terminateWhen(s -> s.allResolved()).or(new
MaxIterationsTermination<>(20))` — and the most severe decision always
surfaces, regardless of evaluation order.

One surprise during implementation: qhorus has no programmatic
observer registration. `ChannelManager` manages channel lifecycle
(create, delete, pause) but has no `addObserver()`. Message observers
are discovered via CDI `Instance<MessageObserver>` in
`MessageObserverDispatcher`. The observer had to implement
`MessageObserver` and be produced as a CDI bean — which turned out
cleaner than the programmatic approach anyway, since qhorus already
knows how to route messages to matching observers by channel name.
