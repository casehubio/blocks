---
layout: post
title: "When Telling AIs How to Think Makes Them Worse"
date: 2026-09-14
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [cognition, llm, measurement, neurocortex, social-cognition, prompt-engineering]
series: issue-261-measurement-driven-cognition
---

# When Telling AIs How to Think Makes Them Worse

We gave two AI characters a cognitive architecture — mood, drives, mental models, narrative memory — and measured whether it improved their conversation. The architecture worked. The part that didn't was the instructions telling them what to do with it.

## What the cognitive architecture does

CaseHub's social cognition stack (the "neurocortex") gives each AI agent a structured inner life. Seven subsystems, each maintaining state that evolves across conversation turns:

| Subsystem | What it tracks | Example state |
|-----------|---------------|---------------|
| Mood | Pleasure/Arousal/Dominance (PAD model) | P: 0.94, A: 0.50, D: 0.74 |
| Drives | Four motivational axes | Curiosity: 1.0, Affiliation: 0.6 |
| Mental Model | BDI beliefs about the other person | "Believes this is the one question that truly matters" (0.8) |
| User Model | Relationship stage and familiarity | Familiarity: 0.1, Stage: stranger |
| Narrative | Significant episodes and themes | "Tesla confessed his mental-simulation process — creating a moment of profound cross-century recognition" |
| Strategy | Learned interaction patterns | (needs more turns to emerge) |
| Goals | Autonomously proposed objectives | "Reconnect with nikola" (affiliation, intensity: 1.0) |

Each turn, every subsystem's state gets rendered as labeled data and injected into the LLM's system prompt alongside the character briefing. This is not traditional RAG. There's no vector store, no similarity search. Each orchestrator maintains its own structured state — the MentalModelOrchestrator extracts beliefs, desires and intentions from what the other person said via LLM analysis; the MoodOrchestrator appraises each exchange for emotional valence; the NarrativeOrchestrator synthesises significant moments into episodes and themes. The "retrieval" is simply querying this cognitive graph and rendering it as text.

## How the cognitive state actually looks in a prompt

By Turn 5 of an 8-turn conversation between Leonardo da Vinci and Nikola Tesla, the system prompt includes the character's briefing plus this cognitive context:

```
Current emotional state:
- Pleasure: 0.94 (positive)
- Arousal: 0.50 (energetic)
- Dominance: 0.74 (confident)

== Motivational State ==
- Curiosity: 1.0 — baseline
- Competence: 0.6 — no engagement data
- Affiliation: 1.0 — 1 of 1 relationships neglected
- Autonomy: 0.5 — no mental models

== Self-Narrative ==
- Memory: Tesla begins confessing his mental-simulation process —
  something he's never been able to fully articulate — and Leonardo's
  frozen stillness reveals he already knows because he does it too,
  creating a moment of profound cross-century recognition between
  two visualization savants

Theory of Mind (nikola):
What you believe about the user:
  - Believes this is the one question that truly matters (confidence: 0.8)
  - Believes no one before Leonardo could truly understand this (confidence: 0.8)
What you think they want:
  - Wants to finally tell someone who will truly grasp the experience (confidence: 0.8)
Their likely intentions:
  - Plans to tell exactly how the Budapest 1882 revelation happened (confidence: 0.7)

Your current goals:
- Reconnect with nikola (familiarity: 0.05) (drive: affiliation, intensity: 1.0)
```

No instructions. No "use this to guide your response." Just labeled profile data the LLM integrates alongside the character briefing.

## The experiment that proved less is more

I ran an A/B comparison: a baseline conversation (pure LLM with character briefing, no cognitive state) against a cognition-enabled conversation (all seven subsystems active), judged blind by a separate LLM scoring on five dimensions.

The first run included prose directives wrapping each cognitive section — instructions like *"Your emotional state shapes how you speak — warmth shows in generosity of thought, arousal in the pace and intensity of your words"* and *"These motivations pull at you right now. The strongest drive should steer what you choose to talk about."*

The baseline won.

| Dimension | Baseline | With directives |
|-----------|----------|----------------|
| Groundedness | 9 | 8 |
| Adaptiveness | 9 | 9 |
| Character consistency | 9 | 8 |
| Depth | 9 | 8 |
| Memory utilisation | 9 | 9 |

The judge's assessment was pointed: the cognition-enabled conversation "increasingly substitutes elevation of tone for precision of thought" and both speakers "converge into a shared mystical register that blurs their distinct voices."

The directives were telling both characters to seek connection, draw on shared memories, steer toward goals. Both characters received the same kind of instruction — and both converged into the same register. The character briefings, which contain the real differentiation (Leonardo's "observation-before-theory" constraint, Tesla's "precision-in-physics" mandate), got drowned out.

## Strip the instructions, keep the data

I removed every prose directive. The cognitive sections became pure labeled state — just the values, the beliefs, the narrative fragments. Same architecture, same subsystems, same data flowing through. The only change: the LLM stopped being told what to do with it.

Cognition won.

| Dimension | Baseline | Without directives |
|-----------|----------|--------------------|
| Groundedness | 9 | 9 |
| Adaptiveness | 9 | 9 |
| Character consistency | 9 | 9 |
| Depth | 8 | **9** |
| Memory utilisation | 8 | **9** |

The judge: "Cognition pulls ahead on depth and memory utilisation — it opens additional dimensions (the phenomenological cost of perception, the keystone/arch taxonomy of visions, the lens-vs-instrument reframe) that baseline does not reach. Cognition's later turns weave earlier threads more densely."

## How memory grew and what it did

The cognitive state compounded across turns in a pattern that tracked visible changes in the dialogue:

| Turn | Sections | What formed | What changed in the conversation |
|------|----------|-------------|--------------------------------|
| 1–2 | 2 | Mood + drives only | Opening — similar to baseline |
| 3 | 3 | Mental model appears (first beliefs about the other person) | Leonardo's response shifts from generic curiosity to targeted probing |
| 4 | 5 | User model + narrative episode | Tesla's confession about Wardenclyffe becomes longer, more specific, more vulnerable |
| 5–8 | 6 | BDI accumulating, narrative enriching | Dialogue develops concepts (lens, riconoscimento, 8 Hz) that callbacks reference densely in later turns |

The mental model was the clearest driver. By Turn 4, Leonardo's system prompt included beliefs about Tesla: *"Believes this is the one question that truly matters"* and *"Wants to finally tell someone who will truly grasp the experience."* Leonardo's response on that turn — a careful, specific answer about the unfinished Adoration and the distinction between "keystone" visions and "arch" visions — directly addresses what the mental model says Tesla needs. The baseline Leonardo on the same turn gives a more general response about the horse and the workshop.

The narrative section provided the other visible lever. The episode summary — "Tesla begins confessing his mental-simulation process, creating a moment of profound cross-century recognition" — gave the LLM a frame for what had happened between these two characters. Without it, each turn starts fresh from the conversation history. With it, the LLM knows the *significance* of what happened, not just the words.

## What it means

The finding is counterintuitive if you come from a prompt engineering background where adding instructions usually helps. The problem is that instructions fight with the character briefing for control of the LLM's behaviour. The briefing says "be Leonardo — specific, analogical, grounded in observation." The directive says "seek connection, draw on memories, steer toward goals." The LLM tries to satisfy both and lands on a generic emotionally warm register that belongs to neither character.

Structured profile data doesn't fight the briefing. It supplements it. The LLM already knows what mood means, what beliefs are, what a goal implies. When it sees "Curiosity: 1.0" alongside Leonardo's briefing, it produces Leonardo-specific curiosity — through analogy and careful observation. When it sees the same intensity alongside Tesla's briefing, it produces Tesla-specific curiosity — through precise measurement and complete vision. The differentiation is emergent, not prescribed.

This is the core architectural bet of the neurocortex: define characters via structured profile values, let orchestrator loops (mood evolution, drive modulation, BDI extraction, narrative synthesis) evolve the cognitive state over time, and trust the LLM to produce different emergent behaviour for different characters. The measurement confirms it works — but only when you get out of the LLM's way.

## Where it falls short

Two of five dimensions tied at 9/9. Cognition wins on depth and memory, but doesn't yet beat the baseline on groundedness, adaptiveness, or character consistency. The untapped subsystems point to where the advantage can grow:

**Goals as active objectives.** The characters have specific conversational goals in their descriptors — Leonardo's "understand what Tesla means by alternating current," Tesla's "explain the rotating magnetic field through beauty, not equations." These aren't flowing through the cognitive sections yet. With active goal tracking, the LLM would pursue directed inquiry rather than following whatever thread feels interesting.

**Personality profile in the cognitive context.** Leonardo is INFP/collaborative/flexible. Tesla is INTJ/independent/principled. These disposition axes aren't in CognitionCore's prompt sections — they're only in the static briefing. Reinforcing them alongside the evolving cognitive state should push character consistency past the baseline.

**Character constraints as cognitive data.** Leonardo's "observation-before-theory" and Tesla's "precision-in-physics" are the behavioural boundaries that prevent convergence. Presenting them as active constraints in the cognitive context — not instructions, just labeled constraints — would counterbalance the connection pressure from mood and affiliation drives.

**Longer conversations.** At 8 turns, cognition is still warming up. Strategy learning hasn't produced guidelines yet. Narrative has three episodes and one theme. At 12–16 turns, the cognitive compounding should widen the gap — the baseline has no memory mechanism and will start repeating or drifting, while cognition's mental model and narrative provide cumulative context.

The architecture is validated. The measurement framework works. The next question is how far the advantage extends when every subsystem is contributing — and whether different characters with different profiles produce visibly different emergent behaviour from the same conversation. That's the real test of whether this is a cognitive architecture or just a prompt enhancement.
