# 0003 — PROPOSE message type for commissive speech acts

Date: 2026-08-14
Status: Accepted

## Context and Problem Statement

qhorus's `MessageType` enum covered directives (COMMAND), interrogatives (QUERY), assertives (RESPONSE/DONE), and refusals (DECLINE), but was missing the **commissive** category — speech acts where the sender commits to action conditional on the receiver's agreement. Building a negotiation protocol (#104) required expressing proposals, which are fundamentally different from commands: "I'll do X if you agree" vs "I direct you to do X."

## Decision Drivers

* FIPA Agent Communication Language (SC00036, SC00037) defines `propose` as a distinct communicative act
* Using COMMAND for proposals creates a semantic mismatch — different deontic force (commissive vs directive)
* RESPONSE on a COMMAND's correlationId auto-fulfills the commitment (GE-20260623-92964b), breaking counter-proposal semantics

## Considered Options

* **Option A** — Add PROPOSE to MessageType (new qhorus enum value)
* **Option B** — Use COMMAND with metadata to carry proposal semantics
* **Option C** — Use COMMAND for proposals, RESPONSE for counter-proposals

## Decision Outcome

Chosen option: **Option A (PROPOSE)**, because it completes qhorus's FIPA speech act coverage and avoids the RESPONSE auto-fulfill trap that would break counter-proposal exchange.

### Positive Consequences

* Correct speech act semantics — proposals are commissive, not directive
* RESPONSE on a PROPOSE correlationId does NOT auto-fulfill (key behavioral difference from COMMAND)
* Completes FIPA coverage: directive (COMMAND), interrogative (QUERY), assertive (RESPONSE/DONE), commissive (PROPOSE), rejection (DECLINE)
* NegotiationProjection dispatches directly on MessageType without metadata workarounds

### Negative Consequences / Tradeoffs

* Requires qhorus change (casehubio/qhorus#395 — implemented and closed)
* CommitmentService needs awareness of originating message type to differentiate PROPOSE vs COMMAND fulfillment behavior

## Pros and Cons of the Options

### Option A — Add PROPOSE to MessageType

* ✅ Correct speech act semantics
* ✅ No auto-fulfill trap for counter-proposals
* ✅ Completes FIPA coverage
* ❌ Requires qhorus change (cross-repo)

### Option B — COMMAND with metadata

* ✅ No qhorus change needed
* ❌ Semantic mismatch (directive vs commissive)
* ❌ COMMAND's commitment direction is wrong (obligates receiver, not proposer)

### Option C — COMMAND + RESPONSE

* ✅ Intuitive ("counter-proposal" sounds like "responding")
* ❌ RESPONSE auto-fulfills COMMAND commitments (GE-20260623-92964b)
* ❌ Counter-proposals would prematurely close the negotiation

## Links

* [casehubio/qhorus#395](https://github.com/casehubio/qhorus/issues/395) — PROPOSE implementation
* [casehubio/blocks#104](https://github.com/casehubio/blocks/issues/104) — Negotiation protocol (consuming use case)
* [FIPA Propose IP (SC00036)](http://www.fipa.org/specs/fipa00036/SC00036H.html)
* [FIPA Communicative Act Library (SC00037)](https://www.fipa.org/specs/fipa00037/SC00037J.html)
