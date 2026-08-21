---
title: The Profile That Builds Itself
date: 2026-08-20
author: Mark Proctor
tags: [casehub, blocks, user-model, cbr, personalisation, relationship]
issue: 122
epic: 126
entry_type: note
subtype: diary
---

# The Profile That Builds Itself

An agent that remembers every interaction but can't tell you who it's talking to has a data problem dressed up as a knowledge problem. CBR stores accumulate cases — "on Tuesday, the user asked about deployment pipelines and seemed frustrated" — but no amount of case retrieval synthesises the conclusion that this person is a DevOps engineer who prefers direct communication and gets impatient with small talk. That synthesis is what the persistent memory literature (arXiv:2510.07925) calls a user profile, and the gap between raw cases and holistic understanding is where personalisation either happens or doesn't.

UserModel is blocks' answer to that gap. It sits in `io.casehub.blocks.agentic.social` alongside PersonalityEvolution and InnerLife — the three form a social cognition triad: how the agent changes, when it speaks, and what it knows about others.

## Who counts as a user

The "user" in UserModel is anyone the agent interacts with. Not necessarily a human — another agent, a bot, a system account — anything identified by a string ID. The profile key is a triple: `(agentId, subjectId, tenantId)`. Agent A's understanding of User X is independent of Agent B's understanding of that same person. This is psychologically correct: my model of you is shaped by our interactions, not by your interactions with someone else.

The decision to decouple from `AgentDescriptor` matters. In wacky-manor, the subject is a Minecraft player identified by UUID. In devtown, it's a GitHub user. In clinical, it's a patient. None of these have eidos descriptors. Requiring one would have excluded every primary consumer.

## The volume problem

The naive approach to familiarity scoring — count positive signals, count negative ones, compute a ratio — breaks immediately. One positive interaction produces a ratio of 1.0, which maps to the "confidant" stage. A single "hello" shouldn't make someone your closest friend.

Laplace smoothing (adding 1 to the denominator) dampens small samples but not enough. A single positive signal still scores 0.75, well into "friend" territory. The fix is a volume factor — a sigmoid-like curve that starts near zero and approaches 1.0 as interaction count grows:

```java
double volumeFactor = 1.0 - 1.0 / (1.0 + total * 0.1);
double score = sentimentNorm * volumeFactor;
```

At 1 interaction, the volume factor is 0.09. At 10, it's 0.5. At 50, it's 0.83. The score requires both positive sentiment AND sustained interaction before it reaches high stages. This is the difference between a model that produces meaningful stage transitions and one that oscillates between stranger and confidant on every second message.

## Tiered synthesis

Most profile dimensions don't need an LLM. Familiarity score is arithmetic. Stage resolution is a threshold lookup. Interaction frequency is a counter. Positive and negative signal counts are increments. All of these update on every tick at zero cost.

The dimensions that need an LLM — communication style, topics of interest, behavioural preferences — are the ones that require interpreting free-text signal descriptions. "Prefers formal language," "frequently discusses security concerns," "responds better in morning meetings" — these can't be computed from signal counts. They require natural language synthesis.

The gate controls when that synthesis fires: only when enough new textual signal has accumulated AND a cooldown period has elapsed. An agent modeling thirty subjects doesn't invoke thirty LLM calls per tick. Most ticks update counters and move on.

## The SPI that hides the hack

`CbrCaseMemoryStore` is the backing store, but CbrCase is a problem/solution/outcome record designed for episodic reasoning. A user profile isn't an episode. Exposing CbrCase semantics to consumers would force every caller to deal with empty `solution` fields, post-filter by `producerAgentId`, and construct queries with feature-value maps just to look up Alice's profile.

`UserProfileStore` wraps all of that:

```java
public interface UserProfileStore {
    void store(UserProfile profile);
    Optional<UserProfile> lookup(String agentId, String subjectId, String tenantId);
    List<UserProfile> findByAgent(String agentId, String tenantId);
    void eraseSubject(String subjectId, String tenantId);
}
```

The `eraseSubject` method is the GDPR path — it scans all agents' profiles for a given subject and erases each one. The default `CbrUserProfileStore` adapter handles the CbrCase conversion, supersession for temporal versioning, and feature-value mapping in one place. Consumers see profiles, not cases.

## What's next

The queue has three patterns remaining. #123 is MentalModel — Theory of Mind with BDI tracking. Where UserModel answers "what do I know about this person?", MentalModel answers "what does this person believe, want, and intend to do?" The first is observation; the second is inference. Both feed the same goal: an agent that adapts not just to what you say, but to who you are.
