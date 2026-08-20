---
title: The Mind That Reads Minds
date: 2026-08-20
author: Mark Proctor
tags: [casehub, blocks, mental-model, theory-of-mind, bdi, goap, epistemic]
issue: 123
epic: 126
entry_type: note
subtype: diary
---

# The Mind That Reads Minds

UserModel tells an agent what someone is *like* — communication style, preferences, relationship stage. But knowing that someone prefers direct communication doesn't tell you that right now, at this moment, they believe the deployment is risky and want it delayed. Stable traits and dynamic cognitive states are different animals. The persistent memory literature (ToMA, ACL 2026) calls the second one Theory of Mind — the capacity to attribute beliefs, desires, and intentions to other actors. Without it, an agent optimises for its own goals while being blind to what the people around it are actually thinking.

MentalModel is the fourth member of the social cognition package. Where PersonalityEvolution tracks how the agent changes, InnerLife governs when it speaks, and UserModel profiles who it's talking to — MentalModel tracks what that person currently believes, wants, and plans to do.

## Three dimensions, three half-lives

BDI — Beliefs, Desires, Intentions — is a classical framework from the 1980s, originally formalised by Bratman and later adopted by the multi-agent systems community. What makes it useful here isn't the formalism. It's the observation that these three dimensions decay at fundamentally different rates.

A belief ("this person thinks the system is fragile") persists. You might hold it for a week without fresh evidence. A desire ("they want a quick fix") is more volatile — it shifts with context, often within a day. An intention ("they plan to escalate to their manager") is the most ephemeral — plans change within hours.

We model this with exponential decay on a per-dimension half-life:

```java
double decayed = confidence * Math.pow(0.5, elapsedMs / halfLifeMs);
```

Beliefs default to a 7-day half-life. Desires get 1 day. Intentions get 4 hours. Below a configurable floor, entries are evicted entirely — an intention you inferred yesterday that hasn't been reinforced simply isn't worth acting on anymore.

## The BeliefSet problem

Blocks already has `BeliefSet<T>` — a proper AGM belief revision implementation with entrenchment ordering and consistency-checked revision. The temptation was to use it as the primary belief container. We couldn't. `Belief<T>` carries key, value, and entrenchment — three fields, no confidence, no timestamp. The entire confidence-decay mechanism requires metadata that BeliefSet was never designed to hold.

The solution: `AttributedState` — a unified record carrying key, description, confidence, entrenchment, timestamp, and BDI dimension — serves as the primary container for all three dimensions. BeliefSet is constructed on-demand for AGM revision when contradictory evidence arrives, then the surviving beliefs are mapped back to `AttributedState` with their confidence and timestamps preserved. BeliefSet provides the revision algorithm; `AttributedState` provides the temporal metadata. Neither replaces the other.

## The epistemic bridge

The conversation infrastructure already classifies dialogue points as ESTABLISHED, PENDING, or DISPUTED through `CommonGroundAnalyser` and `EpistemicRule`. This is epistemic reasoning about what's been agreed in a conversation — but it wasn't connected to per-actor mental state tracking.

`observeConversation(CommonGroundState)` bridges that gap. When a conversation analyser determines that a particular claim is ESTABLISHED (both parties acknowledge it), MentalModel attributes it as a high-confidence belief (0.9) to the subject. PENDING claims become medium-confidence (0.5). DISPUTED points become low-confidence (0.3). The confidence mapping is simple, but the composition is the point — two pieces of infrastructure that were built independently now feed each other.

## From mental state to action

The pragmatic payoff is `project()` — a method that transforms BDI state into conditions a GOAP planner can consume. Each attributed state above a confidence threshold becomes a `MentalProjection`:

```java
record MentalProjection(
    String conditionKey,  // e.g., "deployment_risk"
    boolean value,        // true — the entry exists
    double confidence,    // from the attributed state
    BdiDimension dimension) {}
```

The consumer decides the threshold. An agent might plan differently at 0.5 confidence ("probably stressed") versus 0.9 ("clearly stressed"). The mental model doesn't collapse that judgment into a boolean — it preserves the uncertainty and lets the planner decide how cautious to be.

## The inference gap

Heuristic extraction catches explicit statements — "I think X," "I want Y," "I plan to Z." These are cheap and immediate. But people rarely announce their mental states so directly. The real ToM capability comes from LLM inference on accumulated conversational signals — reading between the lines to attribute beliefs, desires, and intentions that were implied rather than stated.

The LLM fires only when enough new signal has accumulated and a cooldown has elapsed. Most ticks are arithmetic — decay, evict, persist. Inference is the expensive exception, gated the same way UserModel gates synthesis.

The question this raises, and doesn't answer, is how well current LLMs actually perform at Theory of Mind attribution. The academic literature (TimeToM, DPT-Agent) is encouraging but tested in controlled settings. Whether an LLM can reliably infer "this person seems worried about the deadline" from three messages of conversational context — in a way that's useful for downstream planning — is an empirical question we'll find out when quarkmind and devtown start using it.
