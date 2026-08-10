---
layout: post
title: "Two Interfaces, One Vocabulary"
date: 2026-07-12
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [orchestration, unification, shared-types]
series: issue-52-shared-orchestration-types
---

*Part of a series on [#44 — agentic planning architecture](https://github.com/casehubio/blocks/issues/44). Previous: [Closing the Description Thread](2026-07-12-mdp01-closing-the-description-thread.md).*

## The Pattern-Matching Tell

`AgentCardSupport` and `ExecutionEventListener` both had the same shape: a static method that pattern-matched on `AgentRef` variants to extract a name. `AgentCardSupport.candidateName()` checked for `WorkerAgent`, fell back to the class simple name. `ExecutionEventListener.agentName()` had five branches — one per variant — with `ExternalAgent` using an identity hash code as a name substitute because there was nothing better.

Pattern matching to extract the same information from every branch of a sealed hierarchy is the type system telling you something is missing. The fix was making `AgentRef` extend `ExecutorRef` — the shared executor identity interface from engine-api. Each variant now implements `name()` and `description()` at the type level.

After the change, both utility methods collapsed. `agentName()` became `return agent.name()`. `candidateName()` dropped its `instanceof` chain. `buildCard()` lost its `WorkerAgent`-specific description branch — all variants expose description through the same interface.

## Label vs Identity Hash

`ExternalAgent` had no natural name — it's a lambda wrapping a function. The previous `agentName()` used `"external:" + identityHashCode(e)` to distinguish instances in logs. That's a hack: the names are meaningless, unstable across runs, and useless in any audit trail.

The fix is a `label` field on the record. `ExternalAgent("my-tool", fn)` gives the agent a real name. Unlabeled externals return `"external"`. If you need to distinguish two unlabeled externals in monitoring, the answer is to label them — not to hash their identity.

## The `executor()` Bridge

`LeafTask extends TaskDescriptor` was the other half. Both `PrimitiveTask` and `PlannedTask` gained `id` and `createdAt` — the identity fields that make a task trackable. `status()` returns `PENDING`, which is accurate: a task in a plan hasn't started yet. The execution driver creates the mutable lifecycle representation when it picks up the task.

The interesting piece is the vocabulary bridge. The agentic world calls the actor an "agent" and returns an `AgentRef`. The shared monitoring world calls it an "executor" and expects an `ExecutorRef`. `LeafTask` provides a default `executor()` that delegates to `agent()`. One default method, two worlds connected. Any consumer that thinks in executors — monitoring dashboards, compliance audit, the `TaskSnapshot` record — can work with agentic tasks without importing anything from the agentic package.

This is why `AgentRef extends ExecutorRef` had to land first. Without it, `executor()` returning `agent()` would be a type error.

## The Decomposition Factory Question

`Decomposition.primitive()` and `Decomposition.planned()` now generate a UUID and `Instant.now()` for every task they create. That's the right default for production use — every task gets a unique identity at creation time. But it means test code that constructs tasks directly has to provide an id and timestamp, which is more verbose.

I considered adding a test-only factory that generates defaults, but decided against it. The verbosity is useful — it makes test authors think about whether they care about the identity of the task they're constructing. And it keeps the production API honest: id and createdAt are required fields, not optional afterthoughts.
