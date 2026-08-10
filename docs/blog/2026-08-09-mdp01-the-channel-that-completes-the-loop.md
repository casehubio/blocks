---
title: "The channel that completes the loop"
date: 2026-08-09
author: mdp
projects: [casehub-blocks]
entry_type: note
subtype: diary
tags: [agentic, channels, execution-backend, conversation-orchestrator, pattern-builders]
status: draft
---

Blocks had two ways to run multi-agent work, and they didn't talk to each other.

The execution pipeline — OrchestratedDriver — runs a five-phase loop: route an agent, activate, dispatch, aggregate results, check termination. Agents get invoked directly. They never see each other's output. The coordinator holds all the state.

The conversation pipeline — ConversationOrchestrator — does the opposite. Agents share a channel. They see each other's messages. State emerges from the conversation via fold-based projection. Turn policies decide who speaks. Convergence detection decides when to stop.

Both exist in blocks. Both work. But a supervisor pattern that needs to dispatch a collaborative sub-task — a debate between two analysts, a vote across three evaluators — had no way to say "run this over a channel." The primitives were there (qhorus#315 delivered `ChannelManager` and `MessageDispatcher` as service facades), but nothing wired them into the pattern builder DSL.

## The design question

The obvious approach was a parallel API: `ChannelPatterns.debate()` alongside `Patterns.debate()`. Separate composition roots, separate configuration surfaces. Clean separation but doubled surface area.

I didn't like it. The consumer thinks "I'm building a debate" — the channel is a transport decision, not a different kind of debate. Splitting the API forces them to learn two builders when the only difference is where messages travel.

The alternative: `.overChannel()` as a mode switch on existing builders. The pattern builder stays the single configuration surface. Channel support is additive — one method call that swaps the execution backend from direct invocation to channel-based communication.

This works because `ExecutionBackend<T>` was already the right seam. It's a functional interface: take an `ExecutionModel<T>`, return `Uni<ExecutionResult>`. OrchestratedDriver was the only implementation. Now there's `ChannelBackend`, which creates a channel, delegates to a strategy, and tears it down in a finally block.

## What the strategy buys

Not all channel patterns are alike. A debate needs turn coordination, projections, prompt assembly — the full ConversationOrchestrator stack. A vote just needs fan-in: dispatch to all agents, collect results via a COLLECT channel. A barrier is simpler still: dispatch, wait for all contributors.

So `ChannelExecutionStrategy` is a sealed interface with three variants:

- **Conversation** — wraps ConversationOrchestrator. Maps the pattern's agents to participants, infers turn policy from the pattern type (DEBATE → round-robin, VOTING → free-for-all), wires the response dispatcher as a channel feedback loop. The eight-step wiring lives in `ConversationChannelAdapter` — extracted for testability.
- **FanIn** — invokes all agents, maps each result to a `MessageDispatch`, posts to a COLLECT channel.
- **Barrier** — same dispatch, BARRIER semantic. The channel infrastructure handles the synchronisation gate.

Each pattern builder picks its strategy: DebateBuilder uses Conversation, VotingBuilder uses FanIn, ParallelBuilder uses Barrier.

## The type parameter problem

One tension I spent time on: `ExecutionModel<T>` is generic over state type T. ConversationOrchestrator terminates on `ConversationState`, not T. You can't just plug one into the other.

The solution: the Conversation strategy carries its own `TerminationCondition<ConversationState>` and ignores the model's `TerminationCondition<T>`. The builder derives the conversation-native condition from its existing settings — `maxRounds` becomes `MaxIterationsTermination<ConversationState>`, `judge` becomes `JudgeConvergence<ConversationState>`. No type bridge, no unsound cast.

This was the right call. The design review confirmed it — the "captured initial context" approach (passing constant T to every termination evaluation) is semantically wrong for stateful conditions like JudgeConvergence that need the evolving conversation state.

## ComposedAgent closes the composition loop

One more piece: `AgentRef.ComposedAgent` wraps an `ExecutionModel` so a supervisor can dispatch it as a sub-task. Previously, `AgentInvoker.invokeComposed()` always created an `OrchestratedDriver`. Now it checks `model.backend()` first — if a `ChannelBackend` is present, it uses that instead. The supervisor doesn't know or care that the sub-task runs over a channel.

## What's next

The channel observation question — how a supervisor watches a channel-based sub-task's progress via `ChannelProjection` for its own aggregation and termination decisions — is tracked as #97. That's the consumer-facing observation API that builds on top of what landed here.

Cancellation through channel-backed execution (#96) and FanIn/Barrier execution timeout (#99) are the other open threads. The infrastructure handles the happy path; the error paths need design work.
