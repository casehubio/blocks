---
title: The Strategy That Learns from Engagement
date: 2026-08-21
author: Mark Proctor
tags: [casehub, blocks, strategy-learning, metacognition, engagement, cbr, reflection]
issue: 124
epic: 126
entry_type: note
subtype: diary
---

# The Strategy That Learns from Engagement

An agent with a great personality model and a detailed understanding of its users can still be terrible at conversation. It knows Alice prefers directness. It knows Bob cares about security topics. It knows its own personality leans toward thoroughness. None of that tells it whether its last three responses were too long, whether its formality is landing well, or whether asking more questions would increase engagement. The gap between knowing about people and knowing what works with them is metacognition — the agent reflecting on its own interaction strategies and adjusting based on outcomes.

StrategyLearning is the fifth social cognition orchestrator in `io.casehub.blocks.agentic.social`, and it fills a specific cognitive slot the other four don't cover. PersonalityEvolution handles how the agent changes over time. InnerLife handles when it speaks. UserModel handles what it knows about others. MentalModel handles what others think and want. StrategyLearning handles the feedback loop: did this approach work, and what should I do differently?

## The perceive/learn boundary

The design decision that shaped everything else was D41 — the boundary with UserModel. The first-principles question: is "communication style" an observation about the subject, or a strategy for engaging with the subject? UserModel's LLM prompt asks "how does this person communicate?" That's perception — descriptive. StrategyLearning asks "what approach produces good engagement?" That's prescription — learning from outcomes.

Merging them would conflate observation with optimization. A user who "communicates tersely" (UserModel observation) might respond well to verbose explanations (StrategyLearning finding) — the observation and the effective strategy can contradict each other. Keeping them separate preserves the distinction that matters: what IS versus what WORKS.

## Dimensional snapshots, not strategy labels

The initial design had agents labelling their own strategies — recording "I used formal tone" alongside the engagement outcome. The decision review killed this. Strategy labelling requires causal attribution the agent can't reliably provide. Was the engagement drop because of formality, or verbosity, or topic selection?

The fix: dimensional snapshots. When the agent responds, it records its current strategy dimensions — `verbosity: 0.7, formality: 0.3, initiative: 0.5` — alongside the engagement metrics from `EngagementEvent` (did the user respond? how quickly? how long was their reply? did sentiment shift?). No causal attribution needed. The orchestrator accumulates these correlation pairs and TrendAnalyzer detects patterns across them: "when verbosity was above 0.6, continuation rate was 30% lower."

The LLM synthesis tier then reasons about the correlations, not about labels. "Engagement correlates negatively with high verbosity" is a finding from data. "I should be less verbose" is the guideline it produces. The difference between unreliable self-labelling and reliable correlation analysis is the difference between guessing and learning.

## Three tiers, two entry points

The orchestrator follows the dual-cadence pattern from MemoryHygiene — `tick()` for the cheap work, `reflect()` for the expensive work. This was a decision review finding that caught an inconsistency in the original single-tick design.

`tick()` runs tiers 1 and 2. Tier 1 is pure counter arithmetic — how many signals, what fraction got responses, mean sentiment. Tier 2 fires when a conversation boundary arrives: it collects the matched turn outcomes, extracts structured features (average response length, continuation rate, mean sentiment shift, averaged dimensional snapshots), and stores a CBR case. The case becomes evidence for tier 3.

`reflect()` runs tier 3. It retrieves the engagement CBR cases, runs TrendAnalyzer for cross-case trend detection (slope on sentiment, volatility on response length), calls ReflectionOrchestrator for abstract insights, and feeds everything into an LLM synthesis prompt. The output: revised strategy guidelines and dimensional adjustments, clamped to [-0.2, +0.2] per reflection to prevent overcorrection. The updated StrategyProfile is stored and available via `currentStrategy()` for the next interaction.

## What consumers get

```java
orchestrator.currentStrategy(agentId, tenantId)
    .map(StrategyProfile::toPromptSection)
    .orElse("")
```

The profile's `toPromptSection()` renders guidelines as a markdown section that prepends to the agent's system prompt. "Keep responses under three sentences." "Ask follow-up questions when the user mentions a new topic." "With user-42: use informal tone." The agent's behaviour changes because its prompt changes — the most direct feedback loop available in an LLM agent architecture.

For per-subject strategy, `strategyStore.subjectInsights(agentId, subjectId, tenantId)` queries the engagement evidence filtered by subject. The global profile provides defaults; subject-specific evidence lets consumers override when the data supports it.
