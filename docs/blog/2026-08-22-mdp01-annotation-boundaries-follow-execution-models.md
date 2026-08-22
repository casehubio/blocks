---
layout: post
title: "Annotation Boundaries Follow Execution Models"
date: 2026-08-22
entry_type: article
subtype: diary
projects: [casehubio/blocks]
tags: [annotations, langchain4j, orchestration, architecture, adr]
---

CaseHub uses LangChain4j. The `ChatModel` abstraction, the tool-calling pipeline, `@RegisterAiService` — LC4j is our LLM client layer and I'm not looking to replace it. So when I started designing annotation-driven orchestration for CaseHub's agentic patterns, the obvious question was: why not use LC4j's `@SupervisorAgent`, `@SequenceAgent`, and friends?

I looked at the source. The answer turned out to be more interesting than I expected.

## Both systems implement the same patterns

I'd assumed the overlap was limited to five base patterns — supervisor, sequence, parallel, loop, conditional — with CaseHub adding debate, voting, and HTN on top. That's what the original issue said. It's wrong.

LC4j's `langchain4j-agentic-patterns` module already has `DebatePlanner`, `VotingPlanner`, `GoalOrientedPlanner` (GOAP), `BlackboardPlanner`, and `P2PPlanner`. Every pattern CaseHub implements, LC4j implements too. Same vocabulary. Same intent.

Different architecture.

## Two execution models, same vocabulary

LC4j's orchestration is built on the `Planner` interface — a state machine that returns `Action` objects: call these agents, no-op, or done. It's clean, understandable, and exactly right for a general-purpose agent framework:

```java
public interface Planner {
    Action firstAction(PlanningContext planningContext);
    Action nextAction(PlanningContext planningContext);
    default boolean terminated() { return false; }
}
```

CaseHub decomposes every pattern into five independently composable SPIs — routing, decomposition, activation, aggregation, termination — composed into an `ExecutionModel<T>`. A debate is `RoundRobinRouting` + `CollectAll` + `JudgeConvergence`. A supervisor is `LlmSelectedRouting` + `IdentityDecomposition` + `MaxIterationsTermination`. The SPIs plug independently — swap one, keep the rest.

These are not minor differences. They're architectural choices that ripple through everything: how state is managed, how governance hooks integrate, how patterns compose with each other.

## The LLM as orchestrator vs the LLM as one tool

The deepest difference isn't structural — it's about what role the LLM plays.

LC4j's `SupervisorPlanner` delegates every routing decision to an LLM. The `PlannerAgent` is an `AiService` with a system prompt: "You are a planner expert... decide which one of the provided agents to call next." Each iteration sends the agent list, request, and last response to the LLM, which returns the next agent to invoke. The LLM is the orchestrator — it's in the loop for every step.

This is a reasonable design for a general-purpose framework. When you don't know the problem structure upfront, asking the LLM to reason about what to do next is the pragmatic choice.

CaseHub's architecture treats the LLM differently. `LlmSelectedRouting` exists and does exactly what LC4j's supervisor does — asks the LLM which agent to call. But it's one routing strategy among many. `FirstMatchRouting` evaluates rules. `RoundRobinRouting` alternates deterministically. `SequentialRouting` follows a fixed order. And routing is just one of five SPIs — decomposition, activation, aggregation, and termination all operate independently, most without involving an LLM at all.

The planning subsystem shows this even more clearly. `ForwardReasoningDecomposition` does SHOP-style forward reasoning. `CapabilityDependencyDecomposition` does GOAP backward-chaining. `HeuristicDecomposition` does ranked method selection with backtracking. These are classical AI planning techniques — they compute structure from the problem definition, they don't ask the LLM. `LlmDecomposition` exists for when you genuinely need language-level reasoning about how to break a task down. `HybridDecomposition` chains them: try the classical planner first, fall back to the LLM when structure isn't enough.

The distinction matters for reliability and cost. An LLM-in-the-loop supervisor makes an API call on every routing step. A rule-based or GOAP-based orchestrator computes the same decision in microseconds. When you know the problem structure — and in enterprise case management, you often do — classical techniques give you deterministic, auditable, zero-latency orchestration. The LLM earns its place in the gaps: novel situations, ambiguous inputs, decomposition tasks where the problem space isn't pre-mapped.

## Where the implementation diverges

Take GOAP as a concrete example.

LC4j's `GoalOrientedPlanner` builds a dependency graph from agent inputs and outputs, runs a graph search to find a path from preconditions to goal, and executes that path linearly. On failure, it recomputes from current state. About 60 lines of clean, focused code.

CaseHub's decomposition subsystem has `ForwardReasoningDecomposition` (SHOP-style), `CapabilityDependencyDecomposition` (GOAP backward-chaining), `HeuristicDecomposition` (ranked method selection with backtracking and pluggable heuristics), recursive `LlmDecomposition` (multi-level planning via LLM), and `HybridDecomposition` (static-first, LLM fallback). Fifteen-plus classes with output contracts, cost estimation, and heuristic composition.

Both solve "figure out what order to run these agents." LC4j's is a clean, targeted solution for the general case. CaseHub's is an extensible framework for domain-specific planning strategies that draws on classical AI techniques. Neither is wrong — they serve different audiences with different depth requirements.

The debate pattern shows a similar split. LC4j's `DebatePlanner` tracks rounds, convergence, and judge invocation in Planner fields — an imperative state machine. CaseHub's `DebateBuilder` composes from the same five SPIs as every other pattern, with optional integration into qhorus channel-based deliberation, conversation projections, epistemic common ground analysis, and convergence detection across multiple dimensions. Same pattern name, different execution depth.

## Why adoption doesn't work

The strongest argument for adopting LC4j's annotations is: "An annotation is just metadata. Process `@SupervisorAgent` with CaseHub's build extension and generate `ExecutionModel<T>` instead of LC4j's model."

True in principle. Three problems in practice.

**Build extension coexistence.** LC4j's build processor auto-activates when `langchain4j-agentic` is on the classpath and generates LC4j agent beans from `@SupervisorAgent`. If CaseHub's build extension also processes the same annotation to generate `ExecutionModel<T>`, you get duplicate beans or one processor suppressing the other. CaseHub deployments already run pure LC4j agents alongside CaseHub-orchestrated agents — suppressing LC4j's processor kills that coexistence.

**Attribute mismatch.** LC4j's `@SupervisorAgent` has `subAgents`, `maxAgentsInvocations`, `contextStrategy`, `responseStrategy`. CaseHub's supervisor is configured through five SPIs — routing strategy, decomposition strategy, activation rule, aggregation strategy, termination condition — none of which map to LC4j's attribute surface. CaseHub would either ignore most attributes or overload them with different semantics. Neither is honest.

**Semantic lie.** A developer who reads LC4j documentation about `@SupervisorAgent` expects LC4j supervisor behaviour — the `SupervisorPlanner` loop, chat-memory-based context, last-response strategy. In CaseHub, the same annotation would produce a five-SPI `ExecutionModel` with potentially event-driven execution, channel-based deliberation, and governance gate integration. Same name, different contract.

## The dual-track strategy

This isn't "CaseHub vs LC4j." It's recognising that annotation boundaries should follow execution model boundaries — and then maximising integration everywhere else.

**Track 1: CaseHub owns its orchestration annotations.** All eight pattern annotations live in `io.casehub.blocks.annotations`, produce CaseHub's `ExecutionModel<T>`, and compose uniformly with governance meta-annotations like `@OversightGate` and `@TrustRouted`.

**Track 2: LC4j agents are first-class CaseHub workers.** An existing `@SupervisorAgent` LC4j agent is a CDI bean. CaseHub wraps it as a worker. CaseHub handles external orchestration — when to invoke, governance gates, trust-weighted routing, ledger audit. LC4j handles internal orchestration — how the agent does its work. No annotation migration. The LC4j agent gets qhorus channel audit, attestation chains, trust scoring, and oversight gates automatically, because these operate at the worker dispatch boundary, not the annotation layer.

The two tracks reinforce each other. Track 1 ensures CaseHub's orchestration model isn't compromised by annotation-level coupling. Track 2 ensures LC4j agents benefit from CaseHub's governance without rewriting a line. Interoperability through composition, not through annotation adoption.

## Where the collaboration boundary sits

| Layer | Owner | How they interop |
|-------|-------|-----------------|
| LLM client (`ChatModel`) | LC4j | CaseHub uses directly |
| Tool agents (`AiService`) | LC4j | Bridged via `ChatModelAgentProvider` / `AgentProviderChatModel` |
| Orchestration patterns | Each owns their own | Same vocabulary, different execution models |
| Governance | CaseHub | LC4j agents get this via Track 2 |

Spring defines `@Transactional` over Hibernate's `SessionFactory`. It doesn't reuse Hibernate annotations for session management — shared engine, distinct orchestration semantics. CaseHub defines `@Supervisor` over LC4j's `ChatModel`. Same principle.

The cost of owning all eight annotations is small — annotation definitions and build step handlers that map to existing `Patterns.*()` builders. The benefit is architectural clarity and honest API boundaries.

Write in LC4j, deploy in CaseHub, get governance for free. That's the interop story — composition at runtime, not annotation adoption at build time.
