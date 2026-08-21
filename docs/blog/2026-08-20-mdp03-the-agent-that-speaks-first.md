---
title: The Agent That Speaks First
date: 2026-08-20
author: Mark Proctor
tags: [casehub, blocks, agentic, inner-life, proactive, civility]
issue: 119
epic: 126
entry_type: note
subtype: diary
---

# The Agent That Speaks First

Most AI agents are wallflowers. They sit quietly until addressed, produce a response, then go silent again. For task-oriented work — routing, classification, decomposition — this is fine. But agents that participate in social environments need to do what every human in a group chat does naturally: notice something, decide it's worth mentioning, and speak up.

Liu et al.'s CHI 2025 study found 82% user preference for agents with inner thoughts over reactive-only ones. The number is striking but the reason isn't mysterious. A reactive agent feels like a tool you operate. An agent that initiates feels like a participant you interact with. The difference is whether the agent has something going on between your messages.

## Civility before capability

The naive approach — let the LLM decide on every tick whether to speak — has an obvious cost problem (LLM call every few seconds) and a subtler social one. An agent with no social restraint is the person at the party who talks at you unprompted every thirty seconds. Capability without civility is worse than silence.

The InnerLife pattern inverts this. Instead of asking "should I speak?" and paying an LLM call for the answer, it asks "am I *allowed* to speak?" first — for free. The `CivilityConstraint` SPI is a composable gate:

```java
@FunctionalInterface
public interface CivilityConstraint {
    CivilityCheck permitInitiation(InitiationContext context);
}
```

Three defaults ship: `MinimumGapConstraint` (5-minute cooldown between initiations), `MaxPerWindowConstraint` (3 per hour), and `ConsecutiveInitiationCooldownConstraint` (stop after 2 unanswered initiations — if nobody responded, take the hint). All constraints run before any LLM call. First `Denied` kills the tick immediately.

This is Deng et al.'s AAAI 2025 civility taxonomy expressed as a predicate chain. The social science says proactive agents need three properties: intelligence (knowing what to say), adaptivity (knowing when to say it), and civility (knowing when to shut up). Civility is the cheapest to evaluate and the most damaging to skip.

## The novelty gate nobody asked for

Between civility and the LLM sits a content quality gate that turned out to be surprisingly important. Token-level Jaccard distance between the current observation buffer and the previous one — if nothing materially new has happened since the last evaluation, don't bother the LLM. It's a handful of set operations over whitespace-split word bags. Zero cost, and it filters out the majority of ticks where the world hasn't changed.

The interesting wrinkle is the quiet-period bypass. After 30 minutes of silence, the novelty gate stands aside and lets the LLM decide whether elapsed time alone justifies speaking. Without this, the agent is functionally reactive — it only initiates in response to observed events. With it, the agent can genuinely think "it's been quiet, maybe I should check in." That's the difference between a System 1 fast path (react to stimulus) and a System 2 slow path (evaluate whether the absence of stimulus is itself meaningful).

## The pipeline that costs nothing until it doesn't

The three-stage pipeline — civility gate, content quality gate, LLM motivation scoring — is designed so the first two stages are O(1) with zero I/O. Most ticks terminate at stage one or two. The LLM call only fires when both gates pass, which in practice means: something new happened, the agent hasn't spoken recently, and someone actually responded last time.

When the LLM does fire, it receives reflections from the `ReflectionOrchestrator` (what the agent has been thinking about) and affordance context from the consuming app (what channels and actions are available). It returns a motivation score between 0 and 1, content to say, and a suggested channel. If the score exceeds the threshold (default 0.6), the orchestrator returns `Initiated`. If not, `Silent`.

The consuming app controls dispatch. InnerLife produces the decision and the content; it doesn't own the channel, the message format, or the scheduling. It's a library that answers one question: does this agent have something to say right now?

## Thread safety on the hot path

`observe()` is called on every channel event — it has to be O(1). The orchestrator maintains a per-agent event buffer and raw text buffer. `observe()` synchronises on the per-agent state object for an append and a counter increment, then returns. It never touches the tick lock.

`tick()` acquires the tick lock, then briefly acquires the state lock to snapshot and clear the buffers. Once the snapshot is taken, the state lock is released — `observe()` can keep appending to the now-empty live buffers while the pipeline evaluates the snapshot. The LLM call, which dominates tick latency, runs entirely on snapshotted data with no lock held.

This is the same snapshot-then-clear pattern that `InnerLifeOrchestrator` shares with `PersonalityEvolutionOrchestrator` — a recurring theme in these social cognition patterns where a hot observation path must coexist with an expensive periodic evaluation.
