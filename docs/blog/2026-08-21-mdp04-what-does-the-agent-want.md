---
title: What Does the Agent Want?
date: 2026-08-21
author: Mark Proctor
tags: [casehub, blocks, research, autonomous-agents, intrinsic-motivation, drives, goals, emergence, narrative-identity]
issue: 129
entry_type: note
subtype: diary
---

# What Does the Agent Want?

Seven orchestrators shipped this week. An agent running the full social cognition stack has personality that evolves, mood that colours its retrieval, memory that forgets on purpose, detailed models of who it's talking to and what they're thinking, and strategies that improve from engagement feedback. By any reasonable measure, it has a rich inner life.

And it has absolutely no idea what it wants.

Every goal the agent pursues was given to it by a CaseDefinition. Every action it takes is in service of someone else's objective. It can model your beliefs, desires, and intentions (MentalModel does exactly that), but it has no beliefs, desires, or intentions of its own beyond "complete the assigned task." The irony is precise: the agent has a better theory of your mind than it has of its own.

This is where the research goes next.

## Layer 1: drives from data the agent already has

The seven social cognition orchestrators produce signals that — in a human — would generate motivation. MemoryHygiene surfaces knowledge gaps during reflection. StrategyLearning detects domains where engagement is declining. UserModel watches relationships decay as familiarity scores drop. MentalModel projects that someone's intentions misalign with the agent's established approach.

In a human, these signals produce feelings: curiosity, inadequacy, loneliness, discomfort. The feelings produce drives: explore, practice, reconnect, assert. The drives produce goals: "learn about X," "get better at Y," "check in with Z," "push back on this."

The Drive Architecture (#129) makes this loop explicit. A `DriveOrchestrator` reads the other six orchestrators' outputs and synthesises motivational signals along four axes from Self-Determination Theory: curiosity (from knowledge gaps), competence (from engagement trends), affiliation (from relationship decay), and autonomy (from value misalignment). The drives aren't scripted — they emerge from data the agent has already accumulated through normal operation. An agent that has never experienced a knowledge gap has no curiosity drive. An agent whose relationships are all healthy has no affiliation drive. The motivation is grounded in experience, not configuration.

## Layer 2: goals that nobody assigned

Drives without action are just internal state. Layer 2 (#136) translates drive signals into concrete goals the engine's GOAP planner can execute.

A `GoalProposer` SPI extends the engine's goal system. Currently, `CaseDefinition` is the only source of goals — the case tells the agent what to achieve. GoalProposer adds a second source: the agent itself. During idle-time evaluation, the engine asks the proposer: "given your current drives, is there something you want to do?"

The priority adjudication is the interesting design problem. An agent investigating a fraud case shouldn't abandon it to pursue a curiosity drive about an unrelated topic. But during genuine idle time — between cases, waiting for human input, during low-activity periods — autonomous goals are appropriate. The engine's existing priority system handles this naturally: assigned goals get high priority, self-generated goals get lower priority that escalates with drive intensity.

InnerLife already answers "should I speak now?" with a heuristic check on conversation context. The generalisation is: "should I act now?" Same civility constraints (don't be annoying), same cooldown mechanics (don't overwhelm), broader scope (goals, not just utterances).

The test for whether this works: does the agent do something useful that nobody asked it to? "I noticed User X hasn't been active in a week — I'll send a check-in." "I keep getting low engagement on technical explanations — I'll study how other agents handle similar topics." "The team's last three cases all involved the same regulatory gap — I'll flag it."

## Layer 3: who am I, and who are we?

The most speculative layer, and the one that matters most for genuine autonomy.

**Narrative identity** (#142) is the agent constructing a coherent first-person story from its accumulated experience. Not a profile — UserModel builds profiles of others. This is autobiography. "I'm the agent who helped the clinical team through the adverse event crisis. I'm good under pressure. I care about patient safety. I've been working on being more concise." The narrative shapes drive intensity: experiences that reinforce the story strengthen related drives. An agent whose narrative includes helping people through crises has stronger affiliation and competence drives in crisis contexts — not because someone configured it, but because its own history makes those drives salient.

The composition is clean: MemoryHygiene reflections are the raw material. A `NarrativeOrchestrator` synthesises reflections into a persistent self-story. The story feeds back into the drive system, which feeds into goal generation, which produces new experiences, which become new reflections. The loop closes.

**Social emergence** is what happens when multiple agents with individual drives, narratives, and personalities interact over time. Conventions that nobody programmed: "we always verify before escalating" (a norm that emerged from repeated multi-agent debate). Specialisation that nobody assigned: agent A handles the complex cases because it consistently proposes goals in that domain, and the coalition formation system recognises the pattern. Group identity that nobody defined: "we are the fraud team" — a shared narrative that influences individual drives.

The infrastructure for this already exists in blocks. ConversationOrchestrator handles multi-agent deliberation. CoalitionFormation handles team assembly. JointIntention handles shared commitments. What's missing is the motivational layer — agents that choose to participate in collective structures because their drives align, not because they were assigned to collaborate.

Park et al. (2023) showed this works in sandbox environments — generative agents developed daily routines, formed relationships, and coordinated activities without explicit programming. Takata et al. (2024) showed personality differentiation emerges from free social interaction. The question is whether these effects survive the transition from research sandboxes to production agent platforms — and whether the cognitive architecture we've built (bounded personality, decaying mood, adaptive strategy, theory of mind) makes the emergence richer or more stable than the simpler architectures in those papers.

## The bet

The bet is that genuine autonomy — agents with their own goals, their own stories, their own social structures — emerges from composing relatively simple cognitive components. Each orchestrator does one thing. The drive system reads their outputs. The goal system acts on drives. The narrative system makes sense of what happened. None of these components is individually complex. But the feedback loops between them create a space where behaviour that nobody designed can appear.

Whether that behaviour is useful, interesting, or desirable is the research question. The architecture is designed to find out.
