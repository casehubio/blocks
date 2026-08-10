---
layout: post
title: "Wiring the audit spine"
date: 2026-07-01
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
---

The agentic orchestration framework had five SPIs, two drivers, and eight pattern builders — but the data flowing between them was thin. `RoutingContext.task` was the literal string `"task"`. `RoutingDecision.Selected` carried which agents were chosen but not why. The LLM routing prompt couldn't see the accumulated execution state. Debate patterns stored a judge but never invoked it. And nothing was auditable — ExecutionEventListener existed as an interface with no persistent listeners.

I wanted to fix this as one coherent change rather than a series of disconnected patches, because the data model enrichments feed directly into the accountability listeners. The reason field on `RoutingDecision.Selected` is pointless without a listener that persists it. The state renderer on `LlmSelectedRouting` is pointless without a task description to contextualise it. So we treated it as a single data flow spine: RoutingCandidate → RoutingContext → RoutingDecision → ExecutionEventListener.

## Type safety as a design constraint

The hardest question was how to get execution state T into the LLM prompt. Two obvious approaches: pre-serialize it to a String on RoutingContext, or accept a `Function<T, String>` somewhere. Both involve String, but the distinction matters. T is typed state that propagates from the `execute()` call through all five SPIs. Pre-serializing it would break the type chain — downstream consumers would see a String where they should see T.

The answer: `LlmSelectedRouting<T>` takes `Function<T, String> stateRenderer` at construction. T stays typed through the entire pipeline. The String conversion happens at the LLM API boundary — the one place where it's semantically correct. Same principle as serializing a domain object to JSON for an HTTP response: the type is preserved internally, and serialization happens at the edge.

## JudgeConvergence and the typing trick

`JudgeConvergence` wires the debate judge into termination. The interesting design problem: `AgentInvoker<T>` is typed over the execution state, but the judge doesn't operate on execution state — it operates on debate results. We solved this by giving JudgeConvergence its own `AgentInvoker<List<AgentResult>>`. Same functional interface, different type parameter. The judge function receives all accumulated debate results as input, and the type system enforces this at the call site.

The design review caught two builder interaction bugs I'd missed. `SupervisorBuilder.build()` was silently overriding explicit `route()` calls when `stateRenderer` was set — the same class of bug the review had already fixed on DebateBuilder. And `maxRounds()` wasn't clearing the `convergenceExplicitlySet` flag, causing a false mutual-exclusivity error when called after `convergence()`. Both are fixed: SupervisorBuilder applies the renderer eagerly in the setter, and `maxRounds()` resets the flag.

## The dependency tier question

EventLogListener and LedgerExecutionListener need to write to stores that blocks can't reference at compile time — EventLogRepository is in engine runtime, not engine-api, and blocks has no ledger dependency. I initially started designing blocks-local write SPIs, but the real fix is upstream: the write interfaces should be in the API modules, same as the read interfaces already are. We filed cross-repo issues for the extractions and designed the listeners against functional sink interfaces that the API changes will fulfil.

The listeners split cleanly: EventLogListener records everything (operational observability), LedgerExecutionListener records the decision-dispatch-result chain (EU AI Act Art.12 compliance). Both carry the routing reason from `RoutingDecision.Selected` into their audit payloads — which is why enriching the data model first mattered.
