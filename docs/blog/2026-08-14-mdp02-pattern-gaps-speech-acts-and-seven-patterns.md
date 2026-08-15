---
title: "Pattern gaps: speech acts and seven patterns"
date: 2026-08-14
tags: [blocks, negotiation, agentic, speech-acts, fipa]
entry_type: note
subtype: diary
status: published
---

# Pattern gaps: speech acts and seven patterns

The pattern audit flagged eight gaps in blocks' agentic coverage. Seven of them landed today. The interesting one — the one that changed something upstream — was the negotiation protocol.

## The missing speech act

I wanted a `NegotiationProjection` for proposal/counter-proposal exchange. The issue suggested mapping proposals to COMMAND and counter-proposals to RESPONSE. Claude ran an internet search on FIPA speech act theory and came back with a problem: RESPONSE auto-fulfills COMMAND commitments via `CommitmentService.fulfill()`. A counter-proposal sent as RESPONSE would prematurely close the negotiation.

The deeper issue was that qhorus had no commissive speech act. Austin and Searle's taxonomy has five categories. qhorus covered four: directives (COMMAND), interrogatives (QUERY), assertives (RESPONSE/DONE), and refusals (DECLINE). Missing: commissives — "I'll do X if you agree." That's what a proposal IS.

We filed qhorus#395 to add PROPOSE as a new `MessageType`. The key behavioural difference: RESPONSE on a PROPOSE correlationId does NOT auto-fulfill the commitment. This is what makes counter-proposals work — a new PROPOSE supersedes the previous one without accidentally closing it.

The garden entries earned their keep here. GE-20260623-92964b documented exactly the auto-fulfill trap we needed to avoid. Without that entry, we'd have discovered the bug during integration testing instead of during design.

## NegotiationProjection

The projection itself follows the ConversationProjection template but is simpler in every dimension. Concrete class (not abstract — negotiation semantics are fixed by the PROPOSE type). No sentinel metadata parsing (MessageType carries the semantics directly). Party set required upfront (a spec review caught a correctness bug — `UnanimousAcceptance` fires prematurely if parties are discovered incrementally from messages).

The state model is a proposal chain: ordered list of Proposals, per-party Response map on the active proposal, terminal NegotiationOutcome. Counter-proposals supersede the active proposal and clear the response map. `AcceptancePolicy` evaluates quorum on each acceptance — three implementations cover unanimous, majority, and configurable threshold.

Bilateral negotiation is a degenerate case of the multilateral model: two parties, `UnanimousAcceptance` (which naturally requires the one non-proposer to accept). The projection doesn't enforce who may propose — that's an orchestration concern.

## The other six

The consensus gate (#106) turned out to be pure composition — `NegotiationProjection` with `ThresholdAcceptance` and a single round. Documented as a pattern, no new code.

Normative conflict resolution (#107) was the next substantial piece. The gap: when multiple `ActionRiskClassifier` implementations fire contradictory decisions, `ChainedActionRiskClassifier` hardcodes most-restrictive-wins. We built a generic `ConflictResolutionStrategy<T>` with `NormDecision<T>` wrapping any decision type alongside norm metadata — priority, specificity (lex specialis), recency (lex posterior). Five strategies. The spec review caught that `MostRestrictiveResolution` needed to replicate the full `narrower()` algorithm from the existing chain, not just the Autonomous-vs-GateRequired check.

The remaining four were thin composition types — the kind of thing that takes longer to debate than to build:

- **Iterative auction** (#108): `AuctionAggregation` as an `AggregationStrategy<AuctionState>` for English ascending and Dutch descending auctions. Composes with the LOOP execution pattern. `BidExtractor` SPI for parsing agent outputs into `Bid` records.
- **Coalition formation** (#109): `CoalitionProposal`, `CoalitionEvaluator` SPI, `CapabilityCoverageEvaluator`. Scores proposed teams by how well their members' capabilities cover the task requirements.
- **Joint intentions** (#110): Bratman/Cohen-Levesque model as a `JointIntention` with lifecycle state machine (form/activate/reconsider/drop/fulfill) and `IntentionMonitor` SPI for reconsideration triggers.
- **Belief revision** (#111): AGM-style `BeliefSet<T>` with expand/contract/revise operators. `ConsistencyChecker<T>` SPI. Revision removes least-entrenched beliefs to maintain consistency.

## What this opens up

The negotiation protocol is the one with near-term pull — the example slices roadmap (parent#413) needs a negotiation slice. The PROPOSE message type is the prerequisite, and it's already landed in qhorus.

The normative conflict resolution SPI is ready but not wired. Engine's `ChainedActionRiskClassifier` would need to accept a pluggable strategy to replace its hardcoded `mostRestrictive()` reduction. That's an engine-api change — blocks is downstream, so the SPI lives here, the integration lives there.

The academic patterns (#108–#111) are honest about what they are: named types that make these patterns discoverable and teachable. If a use case surfaces, the building blocks exist. If one doesn't, they cost nothing.
