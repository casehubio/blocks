# Decisions — #104 Negotiation Channel Protocol

## D1: Package placement

**Choice:** New `io.casehub.blocks.negotiation` package
**Alternatives:**
- Inside `io.casehub.blocks.conversation` — shares namespace but negotiation has a distinct state model
- Sub-package `io.casehub.blocks.conversation.negotiation` — namespaced under conversation but own types
**Rationale:** Negotiation is semantically distinct from deliberation — different state model, different transitions, different terminal conditions. Clean package boundary with references to conversation types where needed.
**Trade-offs:** Slightly more boilerplate for any shared utilities (imported rather than package-private access).
**Exploration:** quick
**Status:** captured

## D2: Speech-act mapping

**Choice:** PROPOSE (new qhorus MessageType, casehubio/qhorus#395) for proposals and counter-proposals. DONE for acceptance. DECLINE for rejection. Design assumes PROPOSE is available.
**Alternatives:**
- COMMAND for proposals, RESPONSE for counter-proposals — RESPONSE auto-fulfills commitments (GE-20260623-92964b), breaking negotiation
- COMMAND for all proposals with metadata-only semantics — mechanically works but semantically wrong (directive vs commissive)
**Rationale:** First-principles analysis from FIPA speech act theory (SC00036, SC00037). A proposal is a commissive act ("I'll do X if you agree"), not a directive. qhorus is missing the commissive category — PROPOSE fills it. Counter-proposals are new PROPOSEs with new correlationIds. Previous proposal commitments are superseded in the projection.
**Trade-offs:** Depends on qhorus#395 landing. If delayed, COMMAND is the fallback — the projection reads MessageType but the swap is a one-line change.
**Exploration:** deep-analysis
**Depends on:** casehubio/qhorus#395
**Status:** captured

## D3: Projection inheritance

**Choice:** Standalone `ChannelProjection<NegotiationState>` — implements the interface directly
**Alternatives:**
- Extend `ConversationProjection` — inherits infrastructure handling (memos, sub-tasks, flags) but NegotiationState would carry ConversationState's structure with no semantic meaning
**Rationale:** NegotiationState has a fundamentally different shape from ConversationState. No shared infrastructure types (points, threads, sub-task findings) apply to negotiation. Subclassing would add baggage without benefit.
**Trade-offs:** No shared infrastructure handling — if memos or flags become relevant, they'd need separate implementation. Unlikely for a negotiation protocol.
**Exploration:** quick
**Status:** captured

## D4: Multilateral model

**Choice:** Mediator-coordinated multilateral with configurable quorum. Bilateral is a natural special case (two parties, no mediator — roles alternate).
**Alternatives:**
- Bilateral only — simpler but defers a real use case
- Free-form multilateral (any party proposes, concurrent active proposals) — complex state model, harder to reason about
**Rationale:** Mediator-coordinated maps to the supervisor pattern in the agentic package. The mediator issues proposals, parties respond, mediator evaluates quorum and decides next action. Covers the FIPA Iterated Contract Net pattern naturally.
**Trade-offs:** Mediator is a required role in multilateral — no leaderless negotiation. Acceptable for CaseHub use cases where orchestration is always supervised.
**Exploration:** quick
**Status:** captured

## D5: Termination conditions

**Choice:** Reuse `TerminationCondition<NegotiationState>` from the agentic package. Provide negotiation-specific implementations: `MaxRoundsTermination`, `DeadlineTermination`, `AcceptedTermination`. Composable via existing `CompositeTermination`.
**Alternatives:**
- Own termination SPI in the negotiation package — avoids coupling to agentic but duplicates the identical pattern
**Rationale:** TerminationCondition<T> is the platform's generic SPI for evaluating termination from state. The conversation orchestration already provides ConversationState-specific implementations. Negotiation follows the same pattern.
**Trade-offs:** Couples negotiation package to agentic.termination. Acceptable — blocks already has this dependency and the SPI is stable.
**Exploration:** quick
**Status:** captured

## D6: Acceptance quorum

**Choice:** Pluggable `AcceptancePolicy` SPI (`@FunctionalInterface`). Provided implementations: `UnanimousAcceptance` (all parties), `MajorityAcceptance` (>50%), `ThresholdAcceptance` (configurable N-of-M).
**Alternatives:**
- Fixed unanimous — simpler but inflexible
- Configurable integer threshold — less expressive (can't handle weighted voting, veto rights)
**Rationale:** Different negotiation contexts need different quorum rules. A @FunctionalInterface is testable, composable, and consumer-extensible without modifying blocks.
**Trade-offs:** Adds a type and three implementations. Worth it for the flexibility — quorum rules vary by domain.
**Exploration:** quick
**Status:** captured

## D7: Projection concreteness

**Choice:** Concrete class (not abstract). Fixed semantics: PROPOSE = proposal, DONE = accept, DECLINE = reject. Derives everything from MessageType + sender + correlationId. No sentinel metadata parsing needed.
**Alternatives:**
- Abstract with hooks (like ConversationProjection) — more flexible but negotiation has fixed protocol semantics with nothing to customize
**Rationale:** ConversationProjection is abstract because different consumers have different "point initiator" and "status after" semantics. Negotiation semantics are fixed by the protocol — PROPOSE is always a proposal, DONE is always acceptance. No domain variation to hook into.
**Trade-offs:** Less extensible — consumers can't customize what constitutes a proposal. But that's the point: the protocol defines fixed semantics.
**Exploration:** quick
**Status:** captured

## D8: State model

**Choice:** Proposal-chain model. NegotiationState holds an ordered list of Proposals, a set of parties, a per-party response map for the active proposal, and a terminal NegotiationOutcome.
**Alternatives:**
- Event-sourced flat list — simpler fold but every query recomputes, inconsistent with ConversationState's pre-computed pattern
- Dual-state (proposal + consensus) — over-abstracted, splits one state machine into two
**Rationale:** Matches the platform's existing state model pattern (ConversationState is a pre-computed snapshot). Handles bilateral (1 response entry) and multilateral (N response entries) naturally. Fold is pure and testable.
**Trade-offs:** Slightly more complex fold than append-only event list. Worth it for O(1) state queries.
**Exploration:** quick
**Status:** captured

## D9: Metadata-free projection dispatch

**Choice:** Derive negotiation semantics directly from MessageType (PROPOSE, DONE, DECLINE) + sender + correlationId. No sentinel metadata parsing.
**Alternatives:**
- Sentinel-based metadata (like ConversationProjection) — encode entryType, round, proposal terms in ChannelMessageMeta headers. More extensible but unnecessary when PROPOSE provides direct MessageType semantics.
- Hybrid — derive core semantics from MessageType, parse optional metadata for structured terms. Over-engineered for the core protocol.
**Rationale:** With PROPOSE in qhorus (qhorus#395), the message type alone carries the negotiation semantic. PROPOSE = proposal, DONE = accept, DECLINE = reject. Round number is derived from proposal count (deterministic in a channel projection where messages arrive in order). Proposal content is `message.content()`. No metadata layer needed.
**Trade-offs:** Less extensible than the sentinel pattern. If structured terms (price, quantity, conditions) are needed, consumers parse `message.content()` themselves — the projection doesn't need term structure. This is an architectural divergence from ConversationProjection's metadata pattern, justified by the fixed protocol semantics that PROPOSE provides.
**Exploration:** quick (surfaced by decision review R1-03)
**Depends on:** D2, D7
**Status:** captured
