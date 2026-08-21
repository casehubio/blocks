---
title: The Agent That Wants
date: 2026-08-21
author: Mark Proctor
tags: [casehub, blocks, drive-architecture, intrinsic-motivation, social-cognition]
issue: 129
entry_type: note
subtype: diary
---

# The Agent That Wants

The research blog asked "what does the agent want?" Today it got an answer — or at least, the machinery to produce one.

Drive Architecture is the eighth pattern in the social cognition stack, and it's architecturally different from all the others. The seven orchestrators that shipped last week follow the same shape: `record()` pushes signals in, `tick()` processes them, consumers read the output. Drives break this pattern. There's nothing to push — drives are derived from data the other orchestrators already accumulated. A `DriveOrchestrator` pulls from four source orchestrators, modulates by personality and mood, and produces a motivational profile. `tick()` without `record()`. A compositor, not an accumulator.

The four axes map to specific data sources. CuriosityDrive reads knowledge gap signals from MemoryHygiene — how many memories are low-retention, how fragmented is the agent's knowledge. CompetenceDrive reads engagement trends from StrategyLearning — are interaction dimensions declining or improving. AffiliationDrive scans familiarity decay across UserModel subjects — which relationships are neglected. AutonomyDrive counts high-confidence intention projections from MentalModel — how much external pressure is the agent under.

The mapping isn't arbitrary. Three of the four axes come from Self-Determination Theory (Deci & Ryan 2000): autonomy, competence, relatedness. The fourth — curiosity — comes from intrinsic motivation research (Oudeyer & Kaplan 2007, Berlyne 1954). The decision review caught this: the original design attributed all four to SDT, which is wrong. SDT has three axes, not four. Getting the attribution right matters if the theory is going to inform the architecture.

The separation between DriveOrchestrator and DriveComposer turned out to be important. The decision review caught this too — the original plan collapsed composition into the orchestrator, but the research blog and issue epic both modelled them as separate. Composition is where personality and mood modulation happen. An agreeable agent has stronger affiliation drive. An anxious agent (high arousal) has amplified drives across the board. Low dominance amplifies the autonomy drive — feeling controlled makes you want control. The modulation algebra lives in DriveComposer, cleanly separated from the lifecycle management in DriveOrchestrator.

Personality data comes from `AgentDescriptor.disposition()` — passed as a tick parameter, not read from a store. This is the same pattern PersonalityEvolution and InnerLife use. The decision review caught the original design's assumption that PersonalityEvolutionOrchestrator had a public trait accessor — it doesn't. Personality flows through the descriptor, which is an immutable snapshot of the agent's identity.

Forty-four tests, eleven production classes, four upstream API additions to existing orchestrators. The upstream changes are all additive: `knowledgeGaps()` on MemoryHygieneOrchestrator, `engagementTrend()` on StrategyLearningOrchestrator, `activeProfiles()` on UserModelOrchestrator, `activeSnapshots()` on MentalModelOrchestrator. Each one exposes data that was already computed internally but not accessible from outside.

What the agent wants is still abstract — Layer 1 produces a motivational profile, not a plan. Layer 2 (#136) will translate drives into GOAP goals the engine can execute. Layer 3 (#142) will add narrative identity that feeds back into drive modulation. But the foundation is laid: an agent can now report that it's curious about knowledge gaps, dissatisfied with declining engagement, missing neglected relationships, or resisting external pressure. Whether those reports are useful — whether they lead to behaviour that nobody asked for but everyone benefits from — is the question Layer 2 answers.
