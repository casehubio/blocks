# Negotiation Channel Protocol — Design Spec

**Issue:** casehubio/blocks#104
**Branch:** issue-105-pattern-gaps
**Date:** 2026-08-14
**Depends on:** casehubio/qhorus#395 (PROPOSE message type)

## Summary

A reusable `NegotiationProjection` and supporting types in `io.casehub.blocks.negotiation` for managing proposal/counter-proposal exchange over qhorus channels. Supports bilateral (two-party alternating) and mediator-coordinated multilateral (N-party with configurable quorum) negotiation.

## Architecture

### Package: `io.casehub.blocks.negotiation`

New top-level package in blocks, peer to `conversation`, `channel`, `agentic`. No inheritance from or coupling to conversation infrastructure — negotiation has a distinct state model and fixed protocol semantics.

### Speech-Act Mapping

Proposals use the new PROPOSE message type (qhorus#395), which is a commissive speech act — the sender commits to action conditional on acceptance. This completes qhorus's coverage of the FIPA communicative act categories.

| Negotiation action | MessageType | Commitment effect |
|---|---|---|
| Initial proposal | PROPOSE | Opens commitment (proposer conditionally commits) |
| Counter-proposal | PROPOSE (new correlationId) | Opens new commitment; previous superseded in projection |
| Accept | DONE | Fulfills proposal's commitment |
| Reject | DECLINE | Declines proposal's commitment |
| Withdraw | DECLINE (sender == proposer) | Declines own proposal, exits negotiation |

The projection distinguishes withdrawal from rejection by comparing the DECLINE sender against the active proposal's proposer. No metadata needed.

Counter-proposals are new PROPOSE messages with new correlationIds. The previous proposal's commitment is semantically superseded — the projection tracks this. The old commitment is left OPEN and resolves via explicit DECLINE when the negotiation terminates, watchdog expiry, or consumer cleanup.

### Bilateral vs Multilateral

**Bilateral** (two parties): In the typical flow, roles alternate. Party A proposes, Party B accepts/rejects/counter-proposes. On counter-proposal, roles swap — B is now the proposer. The projection does not enforce alternation — both parties can send PROPOSE at any time. Alternation is a convention, not a guarantee.

**Multilateral** (N parties with mediator): The mediator issues proposals (PROPOSE) to all parties. Each party responds with DONE (accept) or DECLINE (reject). The mediator evaluates responses against a configurable `AcceptancePolicy`:
- If quorum reached -> negotiation AGREED
- If not -> mediator synthesises a revised proposal (new PROPOSE) from rejection feedback
- If termination condition fires -> DEADLOCKED

The projection does not enforce who may propose. Any party's PROPOSE creates a new proposal regardless of the negotiation mode. Modal enforcement (only the mediator proposes in multilateral mode) belongs in the orchestration layer, not the projection.

Bilateral is a degenerate case of the same state model: 2 parties, no mediator enforcement, `UnanimousAcceptance` (which naturally requires 1 non-proposer acceptance = the other party).

## Types

### Party set — required upfront

The set of negotiating parties is a constructor parameter of `NegotiationProjection`, not discovered from messages. This is essential for AcceptancePolicy correctness — `UnanimousAcceptance` and `MajorityAcceptance` need the full party count before the first response. Without it, the first acceptance in a 4-party negotiation would trigger unanimous agreement because only 1 non-proposer party is known.

NegotiationState is initialized with the full party set. Messages from unknown senders are logged and ignored.

### ProposalStatus and CommitmentState

ProposalStatus is a projection-level abstraction. The underlying qhorus commitment lifecycle is managed by CommitmentService independently:

| ProposalStatus | CommitmentState | Relationship |
|---|---|---|
| ACTIVE | OPEN | Direct mapping |
| ACCEPTED | FULFILLED | DONE message triggers both |
| REJECTED | DECLINED | DECLINE message triggers both |
| SUPERSEDED | OPEN (stale) | Projection tracks supersession; commitment stays OPEN until consumer explicitly closes it or it expires via watchdog |

The projection does not write to CommitmentService — it is a read model. Superseded commitments are the consumer's responsibility to close.

### Terminal state handling

Once `outcome.isTerminal()` is true, `apply()` returns state unchanged for all subsequent messages. Messages after termination are silently ignored (no log, no exception).

### NegotiationOutcome

Enum: PENDING, AGREED, DEADLOCKED, WITHDRAWN. `isTerminal()` returns true for all except PENDING.

### ProposalStatus

Enum: ACTIVE, SUPERSEDED, ACCEPTED, REJECTED. `isTerminal()` returns true for ACCEPTED and REJECTED.

### PartyDecision

Enum: ACCEPTED, REJECTED.

### Proposal

Record: proposalId (correlationId of PROPOSE), proposer, content, round (1-based), createdAt, status. All fields non-null, round >= 1.

### Response

Record: party, decision (PartyDecision), reason (@Nullable), respondedAt.

### NegotiationState

Immutable record: proposals (ordered chain, oldest first), parties (all participants), responses (per-party response to active proposal), outcome. Provides `activeProposal()`, `round()`, `hasActiveProposal()`.

### NegotiationFold

Pure static state transitions: `propose()`, `accept()`, `reject()`, `agree()`, `deadlock()`, `withdraw()`. Counter-proposals supersede the active proposal and clear the responses map.

### NegotiationProjection

Concrete `ChannelProjection<NegotiationState>`. Derives semantics from MessageType + sender + correlationId. No sentinel metadata. Constructor takes `AcceptancePolicy`. apply() never throws (wraps doApply in try/catch). Dispatches on PROPOSE/DONE/DECLINE. Checks acceptance quorum on each DONE via AcceptancePolicy.

### AcceptancePolicy

`@FunctionalInterface`: `boolean isAccepted(NegotiationState state)`. Three provided implementations:
- `UnanimousAcceptance` — all non-proposer parties must accept
- `MajorityAcceptance` — >50% of non-proposer parties
- `ThresholdAcceptance(int minAcceptances)` — at least N acceptances

### NegotiationRenderer

Renders state as markdown: current proposal + responses, pending parties, proposal history.

### Termination conditions

Reuse `TerminationCondition<NegotiationState>` from agentic.termination. Known compromise: `TerminationContext<T>` carries `List<AgentResult>` (agentic-specific, empty for negotiation). Same pattern conversation orchestration uses.

Five implementations:
- `MaxRoundsTermination(int maxRounds)`
- `AcceptedTermination` — checks `outcome == AGREED`, returns Complete
- `TerminalOutcomeTermination` — checks `outcome.isTerminal()`, returns Complete for AGREED, Failed for DEADLOCKED/WITHDRAWN. This is the general-purpose termination for orchestrators that need to react to any terminal outcome.
- `DeadlineTermination(Instant deadline)` — fires when any message's timestamp exceeds the deadline. Evaluated on each `apply()` call. Does NOT fire unprompted at the deadline itself (projections are message-driven, not clock-driven). Orchestrators needing wall-clock deadlines should use `TerminationContext.elapsed()`.
- `NegotiationCompositeTermination` — first-non-Continue-wins composition

### AcceptancePolicy vs AcceptedTermination

AcceptancePolicy is called by the projection (per-message, transitions state to AGREED). AcceptedTermination is a thin adapter for the orchestration layer (checks outcome already set by projection). Sequential, not overlapping.

## Message Flow Examples

### Bilateral — two rounds

```
A: PROPOSE (corrId=p1, content="Price: $100")        -> commitment opens
B: DECLINE (corrId=p1, content="Too expensive")       -> commitment DECLINED
B: PROPOSE (corrId=p2, content="Price: $80")           -> new commitment opens
A: DONE    (corrId=p2)                                 -> commitment FULFILLED, AGREED
```

### Multilateral — mediator with unanimous quorum

```
M: PROPOSE (corrId=p1, content="Split 50/50")         -> commitment opens
A: DONE    (corrId=p1)                                 -> A accepts
B: DONE    (corrId=p1)                                 -> B accepts
C: DECLINE (corrId=p1, content="Want 40/60")           -> C rejects, quorum not met

M: PROPOSE (corrId=p2, content="Split 45/55")          -> new commitment opens
A: DONE    (corrId=p2)                                  -> A accepts
B: DONE    (corrId=p2)                                  -> B accepts
C: DONE    (corrId=p2)                                  -> C accepts, AGREED
```

## Test Coverage Plan

| Test | What it verifies |
|---|---|
| NegotiationFoldTest | Pure state transitions — propose, accept, reject, withdraw, counter-propose supersession, agree, deadlock |
| NegotiationProjectionTest | Message dispatch — PROPOSE/DONE/DECLINE handling, correlationId matching, sender-based withdrawal detection, terminal state immutability, malformed message safety |
| AcceptancePolicyTest | Unanimous, majority, threshold — boundary conditions, proposer exclusion |
| NegotiationRendererTest | Markdown output for bilateral, multilateral, history, pending responses |
| TerminationConditionTest | MaxRounds, Deadline, Accepted — boundary conditions, composition |
| NegotiationIntegrationTest | End-to-end bilateral and multilateral flows including counter-proposals |

## File Inventory

19 production files, 6 test files. All in `io.casehub.blocks.negotiation`.

## Dependencies

- `casehub-qhorus-api` (MessageView, MessageType, ChannelProjection) — existing compile dependency
- `casehub-blocks` agentic.termination (TerminationCondition, TerminationDecision, TerminationContext) — internal blocks reference
- `org.jspecify:jspecify` — existing for @Nullable

No new dependencies introduced.

## Scope Exclusions

- **Negotiation orchestrator** — out of scope for #104. The projection and fold are the building blocks; orchestration is a separate concern.
- **Multi-issue negotiation** — proposals with multiple attributes/dimensions. The content field carries full terms as a string; structured decomposition is a consumer concern.
- **Concession strategies** — how agents decide what to propose next. Consumer logic, not protocol infrastructure.
- **FIPA Contract Net** — task allocation, not negotiation. Covered by existing agentic patterns.
