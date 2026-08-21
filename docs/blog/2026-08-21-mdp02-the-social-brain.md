---
title: The Social Brain
date: 2026-08-21
author: Mark Proctor
tags: [casehub, blocks, social-cognition, autonomous-agents, personality, memory, theory-of-mind, strategy-learning, architecture]
issue: 126
epic: 126
entry_type: note
subtype: diary
---

# The Social Brain

Six orchestrators, one package, one cognitive architecture. What started as "extract some patterns from the research literature" turned into the most coherent module in blocks — a social cognition system where each piece does exactly one job and the boundaries between them are principled, not accidental.

The research survey (30+ papers from ACL 2025/2026, NeurIPS 2023/2025, ICLR 2024, ICML 2025) identified fourteen scholar gaps in how LLM agents handle social interaction. Seven of those mapped to standalone orchestrators. All seven shipped on this branch. Here's the architecture that emerged, why each piece exists, and what it gives an agent that uses the full stack.

## The six cognitive operations

The decomposition wasn't planned top-down. Each pattern was designed independently from its research base, and the cognitive boundary between them only became clear when UserModel and MentalModel forced the question: where does "observation about a person" end and "strategy for engaging with that person" begin?

The answer cut clean:

| Operation | Orchestrator | What it does | Granularity |
|-----------|-------------|-------------|-------------|
| Self-model | PersonalityEvolution | Bounded trait drift from interaction outcomes | Per agent |
| Self-expression | InnerLife | Background thought loop with proactive initiation | Per agent |
| Perception | UserModel | Behavioural profile synthesis — who is this person | Per subject |
| Mind-reading | MentalModel | BDI Theory of Mind — what does this person think/want/plan | Per subject |
| Emotion | Mood | PAD emotional state with bounded decay, mood-modulated retrieval | Per agent |
| Self-improvement | StrategyLearning | Multi-level reflection on what engagement strategies work | Per agent (per-subject evidence) |

Every orchestrator follows the same API pattern: `record()` accumulates signals at O(1) cost, `tick()` runs cheap periodic analysis, and the expensive work — LLM synthesis, reflection, GOAP projection — fires only when enough data has accumulated. Blocks is a library, not a framework. The consumer controls the cadence.

## What an agent gets

Wire all six into an agent and the behaviour changes are tangible:

**Personality that isn't static.** PersonalityEvolution maps interaction outcomes to eidos JPAF disposition signals — an agent that experiences sustained conflict becomes slightly more guarded; one that builds strong relationships becomes slightly more open. The drift is bounded (L2 norm ceiling), damped (negative events attenuated by a configurable factor per the LLMPTBench findings), and reversible. The JPAF pipeline in eidos handles the psychology; the orchestrator handles the feedback loop.

**Proactive conversation.** InnerLife doesn't wait to be spoken to. It maintains an event buffer of observations, runs heuristic checks (has enough happened since last initiation? has the cooldown elapsed? have there been too many unanswered initiations?), and gates LLM evaluation for topic selection. The civility constraint chain prevents the agent from monopolising the conversation. The result: agents that notice things and speak up about them, without becoming annoying.

**Memory that ages.** MemoryHygiene runs a tick → reflect maintenance cycle. Importance scoring (arousal + surprise, heuristic defaults with LLM-backed consumer overrides) drives eviction decisions. Consolidation merges raw memories into summary entries via ContentSummariser. The idle-time reflection pass generates higher-level insights stored as ReflectionEntries — separate from CBR cases to avoid polluting retrieval. An agent with MemoryHygiene running has a memory that works like human memory: important things persist, trivial details fade, patterns emerge from accumulated experience.

**Understanding of others.** UserModel builds a persistent profile per interlocutor — relationship stage from accumulated quality signals (with a volume factor that prevents a single "hello" from making someone a confidant), familiarity decay with inactivity, and LLM synthesis for open-ended dimensions like communication style and topic preferences. MentalModel goes deeper: BDI tracking (beliefs, desires, intentions) with confidence decay and entrenchment-based revision. The `project()` method produces GoapWorldState conditions for action planning — "user believes the deployment is risky (confidence 0.8)" becomes a world-state predicate the engine's GOAP planner can reason about.

**Emotion that colours retrieval.** Mood maintains a dynamic Pleasure-Arousal-Dominance state that decays exponentially toward a personality-defined baseline. A frustrated agent (-P, +A, -D) retrieves problem-resolution memories more readily; a curious agent (+P, +A, +D) retrieves exploratory ones. The orchestrator is the simplest of the seven — pure heuristic, no LLM — because mood is arithmetic: event → PAD delta → clamp → decay. The consumer provides the appraisal mapping (what does "positive feedback" mean in PAD terms) via `MoodSignal`; the orchestrator handles the state machine. `MoodModulatedRetrieval` in neocortex does the retrieval reweighting, using PAD distance to boost mood-aligned memories.

**Strategy that improves.** StrategyLearning closes the loop. It records dimensional snapshots (what strategy was active) alongside engagement outcomes (did the user respond? how long? did they continue?), stores per-conversation evidence as CBR cases, and periodically reflects across the full evidence base using TrendAnalyzer and ReflectionOrchestrator. The output is a StrategyProfile — ranked textual guidelines injected into the agent's system prompt, plus numerical dimension adjustments. An agent running StrategyLearning gets better at being social over time, not just more knowledgeable.

## The boundaries that matter

Three design decisions shaped the architecture more than any amount of implementation work:

**D15/D22: Memory hygiene is infrastructure, not social cognition.** MemoryHygiene lives in `blocks.memory`, not `blocks.agentic.social`. It composes neocortex memory APIs in a way that non-agentic consumers could use — a CBR-based analytics system has the same memory lifecycle needs as an autonomous agent. The other six orchestrators are agent-specific; MemoryHygiene is domain-neutral.

**D30/D41: Perceive versus learn.** UserModel observes ("Alice communicates tersely"). StrategyLearning learns ("short responses to Alice get 80% engagement"). The observation and the effective strategy can contradict each other — a terse communicator might respond best to detailed explanations. Merging perception and strategy into one orchestrator would collapse this distinction. Keeping them separate preserves the insight that what you know about someone and what works with them are different kinds of knowledge.

**D31: Per-orchestrator signals.** Seven orchestrators means seven signal types. The consumer writes seven mapping functions per domain event. This is a real integration cost. The alternative — a unified SocialObservation bus — was considered and rejected because each orchestrator extracts fundamentally different data from the same event. PersonalityEvolution cares about magnitude and polarity for trait activation. UserModel cares about quality counts for familiarity scoring. MentalModel cares about cognitive content for BDI inference. Mood cares about PAD deltas. Forcing these into one type would create a kitchen-sink record that every orchestrator mostly ignores. The consumer cost is manageable; the orchestrator clarity is essential.

## The test numbers

1638 tests across the full blocks module. The seven social cognition orchestrators contributed 311 tests covering validation, tiered processing, CBR round-trips, LLM synthesis with parse-failure graceful degradation, concurrency, GDPR erasure, PAD decay arithmetic, and the cognitive boundaries between patterns. Every orchestrator follows TDD — tests written before implementation, covering correctness, edge cases, error paths, and boundary conditions.
