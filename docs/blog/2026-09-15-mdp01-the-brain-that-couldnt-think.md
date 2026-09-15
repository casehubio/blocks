---
title: "The Brain That Couldn't Think"
date: 2026-09-15
author: mdp
entry_type: note
subtype: diary
series: issue-279-strategy-learning-showcase
projects: [casehubio/blocks]
tags: [social-cognition, cognitive-pipeline, neurocortex, strategy-learning, showcase]
---

# The Brain That Couldn't Think

I started this session wanting to fix one thing: strategy learning wasn't contributing to the showcase dialogue. Tesla and Leonardo would converse for 12 turns and the StrategyPromptSection never produced a single guideline. Simple enough — tune some thresholds, maybe seed initial data.

Then Claude audited the full cognitive pipeline and the picture got worse. We ran parallel audits across all eight orchestrators — mood, drives, user model, mental model, strategy, narrative, goals, memory hygiene — and found a structural pattern: *wired but not flowing*. The components existed, the SPIs were connected, the tick calls happened every turn. But the data never made it through.

## The 1-Signal-Per-Tick Problem

The root cause was embarrassingly simple. Every orchestrator tick drains its signal buffer. Strategy learning needs 3+ signals to form a conversation case. But tick runs every turn, and only 1 signal arrives between ticks. So every tick drained exactly 1 signal, checked "is 1 >= 3?", said no, and discarded it.

It gets worse. The ConversationOutcome path — designed specifically for correlating turns with conversations — was dead code. CognitionCore created each EngagementEvent with a random UUID as its caseId. Every turn got a different ID. When ConversationOutcome arrived and tried to match turns by conversation ID... nothing matched. The matching code was correct. The data it operated on made matching impossible.

The cascade from there was predictable: no stored cases meant no reflection. No reflection meant no StrategyProfile with guidelines. No guidelines meant StrategyPromptSection returned null. That propagated to CompetenceDrive (reads strategy trends — got nothing), which blocked goal proposals for the competence axis. One broken pipe at the top, three dead subsystems downstream.

## What Actually Worked

Not everything was broken. Mood was fully functional — single-signal additive processing, no batch threshold, just works. Mental model was working through its heuristic path: LLM BDI extraction parsed beliefs, desires, and intentions from each utterance, and the heuristic upsert populated the model from turn 1. Narrative worked via a CognitionStack bypass (manual episode extraction, not the production NarrativePipeline). Affiliation drive correctly detected low-familiarity subjects and proposed relationship-building.

The pattern: orchestrators with simple accumulation semantics worked. Orchestrators that needed batch processing, deferred correlation, or multi-stage pipelines didn't.

## The Fix Chain

Seven changes, each unlocking the next:

Signal correlation first — give each conversation a stable ID via CognitiveImpact, accumulate turns in a per-conversation map across ticks, match against accumulated turns when ConversationOutcome arrives. Then async auto-reflect: when tick stores enough cases, fire reflection on a dedicated executor. Lock-free LLM call — snapshot the data under the tick lock, release it, call the LLM, re-acquire to store the result. Tick stays fast; the brain learns in the background.

Cold-start priming came next. Without stored cases, reflect never fires even with the fix. So CognitionStack primes the CBR store on first tick with synthetic interaction cases derived from the agent's descriptor constraints. Tesla's "precision-in-physics" becomes cases modelling formal, measurement-heavy communication patterns. The auto-reflect fires on the primed cases and the LLM synthesises initial guidelines before the first real conversation turn.

Quality derivation fixed UserModel: instead of hardcoding every interaction as NEUTRAL, we derive quality from the mood appraisal's pleasure and arousal axes. High arousal + positive pleasure = POSITIVE. Low arousal + negative pleasure = NEGATIVE. The important case: high arousal + low pleasure — an intense intellectual debate — maps to NEUTRAL, not NEGATIVE. Tesla and Leonardo arguing about electromagnetic theory should register as engagement, not displeasure.

Finally: wire MemoryHygieneOrchestrator into CognitionStack (11-parameter constructor, test defaults), replace the flat curiosity lambda with real CuriosityDrive, add CuriosityGoalMapper at the FULL stage, override the 60-minute goal cooldown to 30 seconds.

## The Architectural Insight

The conversation about fixes surfaced something larger. I'd been thinking about why the cognitive sections felt like decoration on top of a powerful directive. The agent's briefing — static personality, background, goals, constraints — dominates. The LLM treats it as instruction. The cognition sections (mood shifts, drive intensities, learned strategies) read as supplementary context.

The directive teaches the agent WHAT to think. The cognitive pipeline should teach it HOW to think.

The insight: directives should carry identity and hard rules ("do not swear"). Everything else — goals, beliefs, strategies, narrative — should come from neurocortex as emergent cognitive data. Goals have emotional valence, priority, temporal metadata, links to other entities. Beliefs form from observation and decay without reinforcement. Strategies develop from experience. All of it processed by subsystems doing retrieval, sorting, and selection to surface what's relevant now.

The priming pattern we built for strategy is the first step. You don't set up a brain by writing instructions. You load memories and let the brain process them through its normal cognitive loop. The brain wakes up, thinks about what it knows, and cognition emerges.

Filed #283 for the full directive-minimal architecture. This branch demonstrates the principle for strategy learning. The generalisation to goals, beliefs, and a full knowledge import pipeline is the next phase.
