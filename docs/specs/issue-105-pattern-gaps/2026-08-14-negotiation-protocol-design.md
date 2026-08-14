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

Counter-proposals are new PROPOSE messages with new correlationIds. The previous proposal's commitment is semantically superseded — the projection tracks this. The old commitment is left OPEN and resolves via:
- Explicit DECLINE when the negotiation terminates
- Watchdog expiry
- Consumer cleanup

### Bilateral vs Multilateral

**Bilateral** (two parties): Roles alternate. Party A proposes, Party B accepts/rejects/counter-proposes. On counter-proposal, roles swap — now B is the proposer and A must respond. The projection tracks this naturally via the proposal chain.

**Multilateral** (N parties with mediator): The mediator issues proposals (PROPOSE) to all parties. Each party responds with DONE (accept) or DECLINE (reject). The mediator evaluates responses against a configurable `AcceptancePolicy`:
- If quorum reached → negotiation AGREED
- If not → mediator synthesises a revised proposal (new PROPOSE) from rejection feedback
- If termination condition fires → DEADLOCKED

The mediator role is identified by being the proposer in multilateral mode. The projection does not enforce the mediator pattern — it tracks proposals and responses regardless of who proposes. The mediator logic lives in the orchestration layer (consumer).

## Types

### NegotiationProtocol

Constants for the negotiation protocol. Minimal — most semantics derive from MessageType directly.

```java
public final class NegotiationProtocol {
    private NegotiationProtocol() {}

    // Negotiation outcomes
    public static final String OUTCOME_PENDING    = "PENDING";
    public static final String OUTCOME_AGREED     = "AGREED";
    public static final String OUTCOME_DEADLOCKED = "DEADLOCKED";
    public static final String OUTCOME_WITHDRAWN  = "WITHDRAWN";
}
```

### NegotiationOutcome

```java
public enum NegotiationOutcome {
    PENDING,     // negotiation in progress
    AGREED,      // proposal accepted (quorum reached)
    DEADLOCKED,  // termination without agreement
    WITHDRAWN;   // party withdrew

    public boolean isTerminal() {
        return this != PENDING;
    }
}
```

### ProposalStatus

```java
public enum ProposalStatus {
    ACTIVE,      // current proposal awaiting responses
    SUPERSEDED,  // replaced by a counter-proposal
    ACCEPTED,    // accepted (terminal)
    REJECTED;    // rejected (terminal for this proposal, not necessarily for negotiation)

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED;
    }
}
```

### PartyDecision

```java
public enum PartyDecision {
    ACCEPTED,
    REJECTED
}
```

### Proposal

```java
public record Proposal(
    String proposalId,        // correlationId of the PROPOSE message
    String proposer,          // sender of the PROPOSE
    String content,           // proposal terms (message body)
    int round,                // 1-based round number
    Instant createdAt,
    ProposalStatus status
) {
    public Proposal {
        Objects.requireNonNull(proposalId);
        Objects.requireNonNull(proposer);
        Objects.requireNonNull(content);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(status);
        if (round < 1) throw new IllegalArgumentException("round must be >= 1");
    }
}
```

### Response

```java
public record Response(
    String party,             // responder
    PartyDecision decision,
    @Nullable String reason,  // rejection/withdrawal reason
    Instant respondedAt
) {
    public Response {
        Objects.requireNonNull(party);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(respondedAt);
    }
}
```

### NegotiationState

Immutable snapshot of negotiation state. The projection fold returns new instances on each transition.

```java
public record NegotiationState(
    List<Proposal> proposals,             // ordered chain (oldest first, newest last)
    Set<String> parties,                  // all known participants
    Map<String, Response> responses,      // per-party response to active proposal
    NegotiationOutcome outcome
) {
    public NegotiationState {
        proposals = List.copyOf(proposals);
        parties = Set.copyOf(parties);
        responses = Map.copyOf(responses);
        Objects.requireNonNull(outcome);
    }

    public @Nullable Proposal activeProposal() {
        for (int i = proposals.size() - 1; i >= 0; i--) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) return proposals.get(i);
        }
        return null;
    }

    public int round() {
        return proposals.size();
    }

    public boolean hasActiveProposal() {
        return activeProposal() != null;
    }
}
```

### NegotiationFold

Pure state-transition operations on NegotiationState. No parsing, no I/O — just fold mechanics. Static methods, like ConversationFold.

```java
public final class NegotiationFold {
    private NegotiationFold() {}

    /** Add a new proposal. If an active proposal exists, it is superseded. */
    public static NegotiationState propose(NegotiationState state,
                                           String proposalId, String proposer,
                                           String content, Instant createdAt) {
        var proposals = new ArrayList<>(state.proposals());

        // Supersede any active proposal
        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal old = proposals.get(i);
                proposals.set(i, new Proposal(old.proposalId(), old.proposer(),
                    old.content(), old.round(), old.createdAt(), ProposalStatus.SUPERSEDED));
            }
        }

        int round = proposals.size() + 1;
        proposals.add(new Proposal(proposalId, proposer, content, round, createdAt,
                                   ProposalStatus.ACTIVE));

        // Proposer is a party
        var parties = new LinkedHashSet<>(state.parties());
        parties.add(proposer);

        // Clear responses for new proposal
        return new NegotiationState(proposals, parties, Map.of(), NegotiationOutcome.PENDING);
    }

    /** Record a party's acceptance of the active proposal. */
    public static NegotiationState accept(NegotiationState state,
                                          String party, Instant respondedAt) {
        if (state.activeProposal() == null) return state;

        var responses = new LinkedHashMap<>(state.responses());
        responses.put(party, new Response(party, PartyDecision.ACCEPTED, null, respondedAt));

        var parties = new LinkedHashSet<>(state.parties());
        parties.add(party);

        return new NegotiationState(state.proposals(), parties, responses, state.outcome());
    }

    /** Record a party's rejection of the active proposal. */
    public static NegotiationState reject(NegotiationState state,
                                          String party, String reason,
                                          Instant respondedAt) {
        if (state.activeProposal() == null) return state;

        var responses = new LinkedHashMap<>(state.responses());
        responses.put(party, new Response(party, PartyDecision.REJECTED, reason, respondedAt));

        var parties = new LinkedHashSet<>(state.parties());
        parties.add(party);

        return new NegotiationState(state.proposals(), parties, responses, state.outcome());
    }

    /** Mark the active proposal as accepted and the negotiation as AGREED. */
    public static NegotiationState agree(NegotiationState state) {
        if (state.activeProposal() == null) return state;

        var proposals = new ArrayList<>(state.proposals());
        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal p = proposals.get(i);
                proposals.set(i, new Proposal(p.proposalId(), p.proposer(), p.content(),
                    p.round(), p.createdAt(), ProposalStatus.ACCEPTED));
            }
        }

        return new NegotiationState(proposals, state.parties(), state.responses(),
                                    NegotiationOutcome.AGREED);
    }

    /** Mark the active proposal as rejected and the negotiation as DEADLOCKED. */
    public static NegotiationState deadlock(NegotiationState state) {
        if (state.activeProposal() == null) return state;

        var proposals = new ArrayList<>(state.proposals());
        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal p = proposals.get(i);
                proposals.set(i, new Proposal(p.proposalId(), p.proposer(), p.content(),
                    p.round(), p.createdAt(), ProposalStatus.REJECTED));
            }
        }

        return new NegotiationState(proposals, state.parties(), state.responses(),
                                    NegotiationOutcome.DEADLOCKED);
    }

    /** Withdraw from negotiation. */
    public static NegotiationState withdraw(NegotiationState state,
                                            String party, String reason,
                                            Instant withdrawnAt) {
        var responses = new LinkedHashMap<>(state.responses());
        responses.put(party, new Response(party, PartyDecision.REJECTED, reason, withdrawnAt));

        var proposals = new ArrayList<>(state.proposals());
        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal p = proposals.get(i);
                proposals.set(i, new Proposal(p.proposalId(), p.proposer(), p.content(),
                    p.round(), p.createdAt(), ProposalStatus.REJECTED));
            }
        }

        return new NegotiationState(proposals, state.parties(), responses,
                                    NegotiationOutcome.WITHDRAWN);
    }
}
```

### NegotiationProjection

Concrete `ChannelProjection<NegotiationState>`. Derives negotiation semantics from MessageType + sender + correlationId. No sentinel metadata parsing needed.

```java
public class NegotiationProjection implements ChannelProjection<NegotiationState> {

    private static final Logger LOG = System.getLogger(NegotiationProjection.class.getName());

    private final AcceptancePolicy acceptancePolicy;

    public NegotiationProjection(AcceptancePolicy acceptancePolicy) {
        this.acceptancePolicy = Objects.requireNonNull(acceptancePolicy);
    }

    @Override
    public NegotiationState identity() {
        return new NegotiationState(List.of(), Set.of(), Map.of(),
                                    NegotiationOutcome.PENDING);
    }

    @Override
    public NegotiationState apply(NegotiationState state, MessageView message) {
        if (state.outcome().isTerminal()) return state;

        try {
            return doApply(state, message);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "apply() caught unexpected exception — state unchanged", e);
            return state;
        }
    }

    private NegotiationState doApply(NegotiationState state, MessageView message) {
        return switch (message.type()) {
            case PROPOSE -> handlePropose(state, message);
            case DONE    -> handleAccept(state, message);
            case DECLINE -> handleDecline(state, message);
            default      -> state; // ignore non-negotiation messages
        };
    }

    private NegotiationState handlePropose(NegotiationState state, MessageView message) {
        String proposalId = message.correlationId();
        if (proposalId == null) {
            LOG.log(Level.WARNING, "PROPOSE without correlationId — discarded");
            return state;
        }

        String content = message.content() != null ? message.content() : "";
        return NegotiationFold.propose(state, proposalId, message.sender(),
                                       content, message.createdAt());
    }

    private NegotiationState handleAccept(NegotiationState state, MessageView message) {
        Proposal active = state.activeProposal();
        if (active == null) return state;

        // DONE must reference the active proposal's correlationId
        if (!active.proposalId().equals(message.correlationId())) return state;

        NegotiationState updated = NegotiationFold.accept(state, message.sender(),
                                                          message.createdAt());

        // Check if acceptance quorum is reached
        if (acceptancePolicy.isAccepted(updated)) {
            return NegotiationFold.agree(updated);
        }
        return updated;
    }

    private NegotiationState handleDecline(NegotiationState state, MessageView message) {
        Proposal active = state.activeProposal();
        if (active == null) return state;

        // Withdrawal: DECLINE sender == active proposal proposer
        if (active.proposer().equals(message.sender())) {
            String reason = message.content() != null ? message.content() : "";
            return NegotiationFold.withdraw(state, message.sender(), reason,
                                            message.createdAt());
        }

        // Rejection: DECLINE from another party
        if (!active.proposalId().equals(message.correlationId())) return state;

        String reason = message.content() != null ? message.content() : "";
        return NegotiationFold.reject(state, message.sender(), reason,
                                      message.createdAt());
    }
}
```

### AcceptancePolicy

```java
@FunctionalInterface
public interface AcceptancePolicy {
    boolean isAccepted(NegotiationState state);
}
```

### Provided AcceptancePolicy implementations

```java
/** All parties except the proposer must accept. */
public record UnanimousAcceptance() implements AcceptancePolicy {
    @Override
    public boolean isAccepted(NegotiationState state) {
        Proposal active = state.activeProposal();
        if (active == null) return false;
        Set<String> respondersNeeded = new LinkedHashSet<>(state.parties());
        respondersNeeded.remove(active.proposer());
        if (respondersNeeded.isEmpty()) return false;
        return respondersNeeded.stream()
            .allMatch(p -> {
                Response r = state.responses().get(p);
                return r != null && r.decision() == PartyDecision.ACCEPTED;
            });
    }
}

/** More than half of non-proposer parties must accept. */
public record MajorityAcceptance() implements AcceptancePolicy {
    @Override
    public boolean isAccepted(NegotiationState state) {
        Proposal active = state.activeProposal();
        if (active == null) return false;
        long nonProposerCount = state.parties().stream()
            .filter(p -> !p.equals(active.proposer())).count();
        if (nonProposerCount == 0) return false;
        long acceptedCount = state.responses().values().stream()
            .filter(r -> r.decision() == PartyDecision.ACCEPTED).count();
        return acceptedCount > nonProposerCount / 2;
    }
}

/** At least minAcceptances non-proposer parties must accept. */
public record ThresholdAcceptance(int minAcceptances) implements AcceptancePolicy {
    public ThresholdAcceptance {
        if (minAcceptances < 1) throw new IllegalArgumentException("minAcceptances must be >= 1");
    }

    @Override
    public boolean isAccepted(NegotiationState state) {
        if (state.activeProposal() == null) return false;
        long acceptedCount = state.responses().values().stream()
            .filter(r -> r.decision() == PartyDecision.ACCEPTED).count();
        return acceptedCount >= minAcceptances;
    }
}
```

### NegotiationRenderer

Renders negotiation state as markdown for LLM agent context.

```java
public class NegotiationRenderer {

    public String render(NegotiationState state) {
        var sb = new StringBuilder();
        sb.append("# Negotiation Summary\n\n");

        sb.append("**Status:** ").append(state.outcome()).append("\n");
        sb.append("**Rounds:** ").append(state.round()).append("\n");
        sb.append("**Parties:** ").append(String.join(", ", state.parties())).append("\n\n");

        // Current proposal
        Proposal active = state.activeProposal();
        if (active != null) {
            sb.append("## Current Proposal (Round ").append(active.round()).append(")\n\n");
            sb.append("**Proposed by:** ").append(active.proposer()).append("\n");
            sb.append("**Terms:** ").append(active.content()).append("\n\n");

            if (!state.responses().isEmpty()) {
                sb.append("**Responses:**\n");
                for (Response r : state.responses().values()) {
                    String emoji = r.decision() == PartyDecision.ACCEPTED ? "✓" : "✗";
                    sb.append("- ").append(emoji).append(" **").append(r.party())
                      .append(":** ").append(r.decision());
                    if (r.reason() != null && !r.reason().isBlank()) {
                        sb.append(" — ").append(r.reason());
                    }
                    sb.append("\n");
                }
            }

            // Pending responses
            Set<String> pending = new LinkedHashSet<>(state.parties());
            pending.remove(active.proposer());
            pending.removeAll(state.responses().keySet());
            if (!pending.isEmpty()) {
                sb.append("\n**Awaiting response from:** ")
                  .append(String.join(", ", pending)).append("\n");
            }
        }

        // Proposal history
        List<Proposal> history = state.proposals().stream()
            .filter(p -> p.status() != ProposalStatus.ACTIVE)
            .toList();
        if (!history.isEmpty()) {
            sb.append("\n---\n\n## Proposal History\n\n");
            for (Proposal p : history) {
                String statusEmoji = switch (p.status()) {
                    case SUPERSEDED -> "↩";
                    case ACCEPTED -> "✓";
                    case REJECTED -> "✗";
                    default -> "·";
                };
                sb.append(statusEmoji).append(" **Round ").append(p.round())
                  .append("** (").append(p.proposer()).append("): ")
                  .append(p.content()).append(" — ").append(p.status()).append("\n");
            }
        }

        return sb.toString();
    }
}
```

### Termination conditions

Reuse `TerminationCondition<NegotiationState>` from `io.casehub.blocks.agentic.termination`.

**Known compromise:** `TerminationContext<T>` carries `List<AgentResult> results` — an agentic-specific field meaningless for negotiation. Negotiation termination conditions only use `context.state()` and `context.iterationCount()`, constructing the context with `List.of()` for results. This is the same pattern the conversation orchestration already uses (`AllAgreedTermination`, `SupervisorTermination` etc. all ignore `results`). A cleaner SPI extraction (separating protocol-level context from agentic context) is tracked separately but out of scope for #104.

**CompositeTermination:** The existing `CompositeTermination` in `conversation.orchestration` is typed to `ConversationState`. Negotiation provides its own trivial composite — a 10-line class with first-non-Continue-wins semantics.

Three provided implementations plus a composite:

```java
/** Complete when round count reaches maxRounds. */
public record MaxRoundsTermination(int maxRounds) implements TerminationCondition<NegotiationState> {
    public MaxRoundsTermination {
        if (maxRounds < 1) throw new IllegalArgumentException("maxRounds must be >= 1");
    }

    @Override
    public TerminationDecision evaluate(NegotiationState state) {
        if (state.round() >= maxRounds) {
            return new TerminationDecision.Complete("Max rounds reached (" + maxRounds + ")");
        }
        return TerminationDecision.CONTINUE;
    }
}

/** Complete when the active proposal is accepted per the AcceptancePolicy. */
public record AcceptedTermination(AcceptancePolicy policy) implements TerminationCondition<NegotiationState> {
    @Override
    public TerminationDecision evaluate(NegotiationState state) {
        if (state.outcome() == NegotiationOutcome.AGREED) {
            return new TerminationDecision.Complete("Proposal accepted");
        }
        return TerminationDecision.CONTINUE;
    }
}

/** Complete when the negotiation exceeds a deadline. Evaluated against
  * the latest proposal's createdAt as a clock proxy (no wall-clock dependency). */
public record DeadlineTermination(Instant deadline) implements TerminationCondition<NegotiationState> {
    @Override
    public TerminationDecision evaluate(NegotiationState state) {
        if (state.proposals().isEmpty()) return TerminationDecision.CONTINUE;
        Instant latestActivity = state.proposals().getLast().createdAt();
        if (latestActivity.isAfter(deadline)) {
            return new TerminationDecision.Complete("Deadline exceeded");
        }
        return TerminationDecision.CONTINUE;
    }
}
```

```java
/** Evaluates conditions in order; first non-Continue wins. */
public class NegotiationCompositeTermination implements TerminationCondition<NegotiationState> {
    private final List<TerminationCondition<NegotiationState>> conditions;

    public NegotiationCompositeTermination(List<TerminationCondition<NegotiationState>> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        for (var condition : conditions) {
            var decision = condition.evaluate(context);
            if (!(decision instanceof TerminationDecision.Continue)) return decision;
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
```

### AcceptancePolicy composition

`AcceptancePolicy` and termination conditions serve different layers:

- **AcceptancePolicy** is called by `NegotiationProjection.handleAccept()` — it evaluates whether the quorum threshold is met after each individual acceptance and transitions the state to `AGREED` if so. It operates at the projection level (per-message).
- **AcceptedTermination** is a thin adapter for the orchestration layer — it checks `state.outcome() == AGREED` (already set by the projection via AcceptancePolicy). They are sequential, not overlapping.

### Bilateral vs multilateral — projection behavior

The projection does not enforce who may propose. Any party's PROPOSE creates a new proposal regardless of the negotiation mode. Modal enforcement (only the mediator proposes in multilateral mode) belongs in the orchestration layer, not the projection.

For bilateral negotiation, `AcceptancePolicy` should use `UnanimousAcceptance` (the only non-proposer party must accept). For multilateral, the mediator evaluates responses and may use `MajorityAcceptance`, `ThresholdAcceptance`, or a custom policy.

Bilateral is a degenerate case of the same state model: 2 parties, no mediator enforcement, `UnanimousAcceptance` (which naturally requires 1 non-proposer acceptance = the other party).

## Message Flow Examples

### Bilateral — two rounds

```
A: PROPOSE (corrId=p1, content="Price: $100")        → commitment opens
B: DECLINE (corrId=p1, content="Too expensive")       → commitment DECLINED
B: PROPOSE (corrId=p2, content="Price: $80")           → new commitment opens
A: DONE    (corrId=p2)                                 → commitment FULFILLED

Projection state after each message:
1. proposals=[{p1, ACTIVE}], responses={}, outcome=PENDING
2. proposals=[{p1, ACTIVE}], responses={B: REJECTED}, outcome=PENDING
3. proposals=[{p1, SUPERSEDED}, {p2, ACTIVE}], responses={}, outcome=PENDING
4. proposals=[{p1, SUPERSEDED}, {p2, ACCEPTED}], responses={A: ACCEPTED}, outcome=AGREED
```

### Multilateral — mediator with unanimous quorum

```
M: PROPOSE (corrId=p1, content="Split 50/50")         → commitment opens
A: DONE    (corrId=p1)                                 → A accepts
B: DONE    (corrId=p1)                                 → B accepts
C: DECLINE (corrId=p1, content="Want 40/60")           → C rejects

→ Quorum not reached (unanimous requires all 3, got 2/3)

M: PROPOSE (corrId=p2, content="Split 45/55")          → new commitment opens
A: DONE    (corrId=p2)                                  → A accepts
B: DONE    (corrId=p2)                                  → B accepts
C: DONE    (corrId=p2)                                  → C accepts

→ Quorum reached → outcome=AGREED
```

### Withdrawal

```
A: PROPOSE (corrId=p1, content="Price: $100")
B: DECLINE (corrId=p1, content="No deal")              → B rejects

A: DECLINE (corrId=p1)                                  → A (proposer) sends DECLINE
                                                        → projection detects sender==proposer
                                                        → outcome=WITHDRAWN
```

## Test Coverage Plan

| Test | What it verifies |
|---|---|
| `NegotiationFoldTest` | Pure state transitions — propose, accept, reject, withdraw, counter-propose supersession, agree, deadlock |
| `NegotiationProjectionTest` | Message dispatch — PROPOSE/DONE/DECLINE handling, correlationId matching, sender-based withdrawal detection, terminal state immutability, malformed message safety |
| `AcceptancePolicyTest` | Unanimous, majority, threshold — boundary conditions, proposer exclusion |
| `NegotiationRendererTest` | Markdown output for bilateral, multilateral, history, pending responses |
| `TerminationConditionTest` | MaxRounds, Deadline, Accepted — boundary conditions, composition |
| `NegotiationIntegrationTest` | End-to-end bilateral and multilateral flows including counter-proposals |

## File Inventory

| File | Package |
|---|---|
| `NegotiationProtocol.java` | `negotiation` |
| `NegotiationOutcome.java` | `negotiation` |
| `ProposalStatus.java` | `negotiation` |
| `PartyDecision.java` | `negotiation` |
| `Proposal.java` | `negotiation` |
| `Response.java` | `negotiation` |
| `NegotiationState.java` | `negotiation` |
| `NegotiationFold.java` | `negotiation` |
| `NegotiationProjection.java` | `negotiation` |
| `AcceptancePolicy.java` | `negotiation` |
| `UnanimousAcceptance.java` | `negotiation` |
| `MajorityAcceptance.java` | `negotiation` |
| `ThresholdAcceptance.java` | `negotiation` |
| `NegotiationRenderer.java` | `negotiation` |
| `MaxRoundsTermination.java` | `negotiation` |
| `AcceptedTermination.java` | `negotiation` |
| `DeadlineTermination.java` | `negotiation` |
| `NegotiationCompositeTermination.java` | `negotiation` |

18 production files, 6 test files.

## Dependencies

- `casehub-qhorus-api` (MessageView, MessageType, ChannelProjection) — **existing** compile dependency
- `casehub-blocks` agentic.termination (TerminationCondition, TerminationDecision) — **internal** blocks reference
- `org.jspecify:jspecify` — **existing** for @Nullable

No new dependencies introduced.

## Scope Exclusions

- **Negotiation orchestrator** — a `NegotiationOrchestrator` (parallel to `ConversationOrchestrator`) is a natural next step but out of scope for #104. The projection and fold are the building blocks; orchestration is a separate concern.
- **Multi-issue negotiation** — proposals with multiple attributes/dimensions. The `content` field carries the full proposal terms as a string; structured multi-issue decomposition is a consumer concern.
- **Concession strategies** — how agents decide what to propose next. Consumer logic, not protocol infrastructure.
- **FIPA Contract Net** — task allocation, not negotiation. Covered by the existing agentic routing/decomposition patterns.
