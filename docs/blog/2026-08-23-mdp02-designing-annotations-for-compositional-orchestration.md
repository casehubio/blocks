---
layout: post
title: "Designing an Annotation Model for Compositional AI Orchestration"
date: 2026-08-23
entry_type: article
subtype: diary
projects: [casehubio/blocks]
tags: [annotations, orchestration, architecture, quarkus, langchain4j, design]
series: issue-116-blocks-annotations
---

# Designing an Annotation Model for Compositional AI Orchestration

*Continues from [Annotation Boundaries Follow Execution Models](2026-08-22-mdp01-annotation-boundaries-follow-execution-models.md).*

CaseHub's agentic framework has eight orchestration patterns — supervisor, sequence, parallel, loop, conditional, debate, voting, HTN — each built from five composable SPIs: routing, decomposition, activation, aggregation, and termination. The builder API is powerful. A developer can compose round-robin routing with majority-vote aggregation and convergence-based termination in a single fluent chain. But that power has a cost: the builder chain for a governed debate pattern runs to fifteen lines before you reach `.build()`.

The annotation module exists to close that gap. `@Debate(maxRounds = 5)` with `@OversightGate(MyClassifier.class)` produces the same governed debate pattern — without learning the builder API.

The design decisions that shaped this module are more interesting than the implementation. They're about where annotation boundaries belong when your execution model is compositional.

## One annotation, one ExecutionModel

The first decision was the simplest: each pattern annotation maps to exactly one `ExecutionModel`. `@Supervisor` produces a supervisor model. `@Debate` produces a debate model. The annotation attributes map directly to the builder's most-used methods. Annotation defaults match builder constructor defaults — `@Supervisor` with no attributes produces the same model as `Patterns.supervisor().build()`.

This sounds obvious. It wasn't — the alternative was a decomposed annotation model where separate annotations controlled routing (`@Route(FirstMatch.class)`), aggregation (`@Aggregate(MajorityVote.class)`), and termination (`@Terminate(MaxIterations.class)`) independently. That model is more expressive. It's also harder to validate, harder to document, and harder to get wrong in interesting ways. A developer who writes `@Route(RoundRobin.class) @Terminate(JudgeConvergence.class)` has composed a configuration that makes no sense — round-robin routing with judge-based convergence but no judge defined.

The one-annotation-one-model approach means the annotation name carries the semantics. `@Debate` implies round-robin routing, collect-all aggregation, and either judge convergence or max-iterations termination depending on whether a `@Judge` parameter exists. The defaults are the right defaults for that pattern. The developer overrides what they need and trusts the rest.

## Participants live on parameters, not in arrays

The role annotations — `@Agent`, `@Debater`, `@Voter`, `@Judge` — are parameter-level, not nested inside the pattern annotation. A debate looks like this:

```java
@Debate(maxRounds = 5)
String review(
    @Debater(role = "critic", systemPrompt = "Challenge every claim")
    AgentRef critic,
    @Debater(role = "advocate", systemPrompt = "Defend the position")
    AgentRef advocate,
    @Judge(systemPrompt = "Evaluate and decide")
    AgentRef judge,
    String document);
```

Each `AgentRef` parameter carries a role annotation that defines its identity — system prompt for inline agents, or `agentId` for eidos-managed identities. The build extension validates: every `AgentRef` must have exactly one role annotation, and each role annotation must specify exactly one of `systemPrompt` or `agentId`. These are constraints Java's type system can't express. The Jandex scanner enforces them at build time.

The `@Judge` annotation does double duty. It defines the judge as a participant (they speak in the debate like any debater). It also triggers `JudgeConvergence` termination — the debate ends when the judge reaches a verdict, not after a fixed number of rounds. Absent a judge, the debate falls back to `MaxIterationsTermination`. One annotation, two effects, zero configuration overlap.

## Governance composes — it doesn't configure

The four governance annotations — `@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation` — are cross-cutting. They compose onto both pattern methods and `@Worker` methods from the engine layer. A governed debate and a governed worker get the same governance machinery through the same annotations.

```java
@Debate(maxRounds = 3)
@OversightGate(SafetyClassifier.class)
@CbrRouted(successWeight = 0.9, failureWeight = 0.0)
String review(...)
```

The build extension validates that every governance annotation has a target — either a pattern annotation or a `@Worker` on the same method. An `@OversightGate` on a bare method governs nothing, which is always a mistake. Build error, not runtime surprise.

This separation matters architecturally. Pattern annotations configure *what* the orchestration does. Governance annotations configure *how it's supervised*. The same `@OversightGate(SafetyClassifier.class)` can appear on a debate, a supervisor, or a plain worker without any change to its semantics. The classifier doesn't know or care what it's governing.

## The @Customize escape hatch

Annotations handle the 80% case. The remaining 20% — custom routing strategies that need an `AgentProvider`, event-driven backends, complex convergence policies — go through `@Customize`:

```java
@Customize
static void customize(DebateBuilder<?> builder, AgentProvider provider) {
    builder.backend(ExecutionBackend.choreographed(channelSource));
}
```

The build extension validates that the first parameter is a pattern builder type and matches the declaring interface's pattern annotation. Additional parameters are CDI-resolved at runtime. A `@Customize` method on a class with `@Debate` must take a `DebateBuilder`, not a `SupervisorBuilder` — the mismatch is a build error.

This is the same progressive disclosure pattern used throughout CaseHub's annotation layers. Engine-annotations handles `@Case`, `@Worker`, `@Bind` for the 80%. `@Customize` with `CaseDefinition.Builder` handles the rest. Blocks-annotations extends this to orchestration patterns with the same contract.

## Why not LangChain4j's annotations?

CaseHub uses LangChain4j. The `ChatModel` abstraction, the tool-calling pipeline, `@RegisterAiService` — LC4j is the LLM client layer. So the obvious question was: why not use LC4j's `@SupervisorAgent`, `@SequenceAgent`, and friends?

Three structural problems:

**Build extension coexistence.** LC4j's `AgenticProcessor` auto-activates when `langchain4j-agentic` is on the classpath and processes `@SupervisorAgent` to generate LC4j agent beans. If CaseHub's build extension also processes the same annotation, either duplicate beans are generated, one processor must suppress the other, or both run with CDI disambiguation. CaseHub deployments already have LC4j agents alongside CaseHub-orchestrated agents — suppressing LC4j's processor breaks that coexistence.

**Attribute surface mismatch.** LC4j's supervisor is an imperative state machine — `Planner.firstAction()`, `Planner.nextAction()`. CaseHub's supervisor is five composable SPIs. CaseHub would either ignore most LC4j attributes or overload them with different semantics.

**Semantic lie.** A developer who reads LC4j documentation about `@SupervisorAgent` expects LC4j's supervisor behaviour. In a CaseHub deployment, the same annotation would produce different runtime behaviour. Same name, different contract.

The integration path is at runtime, not annotations. An LC4j `@SupervisorAgent` is a CDI bean. CaseHub wraps it via `@Worker` + `AgentWorkerFunction`. CaseHub handles external orchestration — when to invoke, governance, trust routing, audit. LC4j handles internal orchestration — how the agent does its work. No annotation migration needed.

The analogy: Spring defines `@Transactional` over Hibernate's `SessionFactory`. Spring doesn't reuse Hibernate's annotations — the orchestration layers serve different purposes even though they share the persistence engine.

## What the annotation model reveals about the execution model

Designing the annotation layer forced precision about what the execution model actually guarantees. `SequenceBuilder` doesn't set its routing or termination in the constructor — those are only initialised inside `agents()`, because sequential routing needs to know the agent count. Every other builder sets defaults in the constructor. The annotation layer's recorder had to handle this asymmetry, which surfaced a design inconsistency that had been invisible to builder-API users.

`ConditionalBuilder` doesn't expose a public `agents()` method — its API is `when(Predicate, AgentRef)`, designed for programmatic use where you provide the routing predicate. The annotation model has no predicate at compile time. The recorder bypasses the builder entirely and constructs the `ExecutionModel` directly. When a recorder has to bypass the builder to produce the same output, the builder's API surface is too narrow.

These are the kind of findings that only surface when you build a second consumer of the same abstractions. The builder API was the first consumer. The annotation model is the second. The discrepancies between them are design signals worth following.
