# Conversation Folds — Common Ground + Convergence Detection

Post-fold derivation layer over `ConversationState` — epistemic fact
classification and convergence signal detection as configurable pure
functions, not fold extensions.

**Issues:** casehubio/blocks#65 (common ground), casehubio/blocks#66 (convergence detection)
**Related:** casehubio/blocks#49 (topic-aware conversation model), casehubio/qhorus#360 (common ground infra), casehubio/qhorus#364 (convergence infra)
**Research:** [Common ground via mental imagery](https://arxiv.org/html/2604.21144), [Frame of Reference](https://arxiv.org/html/2601.09365v2), [Grounding gaps (NAACL 2024)](https://aclanthology.org/2024.naacl-long.348/), [MAST taxonomy](https://openreview.net/forum?id=wM521FqPvI)

---

## The Problem

Multi-agent conversations accumulate assertions, agreements, and disputes
— but nothing tracks what's been established vs what's pending or
contested. Agents presume common ground rather than building it. And
conversations have no structural termination signal — they run until an
arbitrary round limit or an expensive LLM judge call.

Two features are needed:
1. **Common ground projection** — derive what's established, pending, and
   disputed from the conversation's fold state
2. **Convergence detection** — detect consensus, deadlock, and diminishing
   returns from conversation structure and common ground trajectory

---

## Architecture Decision: Post-Fold Derivation

Both features are implemented as **post-fold derivations** — pure functions
from `ConversationState → DerivedView` — not as fold extensions.

### Why not fold extensions?

1. **Interpretation varies by consumer.** Clinical requires explicit
   per-agent acknowledgement. Drafthouse accepts tacit acceptance (no
   objection within N rounds). AML needs commitment resolution (only
   completed commands are facts). A single fold interpretation is wrong
   for at least one consumer.

2. **Dependency chain.** Convergence consumes common ground (consensus =
   common ground growing, deadlock = common ground stagnant). As functions,
   this composition is natural. As fold extensions, one projection would
   need to feed another mid-fold.

3. **Performance is not a concern.** Conversation state is bounded by
   active points, not history length (the CQRS premise from blocks#49).
   Derivation is O(points × thread_depth) — small.

4. **The door stays open.** If incremental performance ever matters, the
   enriched fold state contains everything needed to build an incremental
   derivation later. But we don't build it until there's a measured need.

### Deviation from issue #65

Issue #65 envisioned a `RenderableProjection<CommonGroundState>` — either
a standalone projection or one composing with `ConversationProjection`.
This spec delivers a stateless utility class (`CommonGroundAnalyser`)
operating on pre-folded `ConversationState` instead.

The projection approach couples the epistemic interpretation to a single
fold strategy. Different consumers need different epistemic rules (see
point 1 above), and a projection would either hardcode one interpretation
or need its own strategy injection — defeating the projection's simplicity.
Post-fold derivation composes naturally: the analyser is a pure function
that any consumer can call with any rule on any already-folded state.

### What the fold does need

Two pieces of information on `MessageView` are currently discarded during
the fold but are required for derivation:

- **`sender`** (agent identity) — needed for participant tracking in
  common ground. Without it, you can't distinguish "same agent responded
  twice" from "two agents responded."
- **`createdAt`** (timestamp) — needed for temporal analysis in
  convergence detection (message density, staleness windows).

These are added to `ThreadEntry` as data preservation, not logic change.

---

## 1. ThreadEntry Enrichment

```java
public record ThreadEntry(
        String entryId,
        Long messageId,
        MessageType messageType,
        String sender,          // NEW — from MessageView.sender()
        Instant createdAt,      // NEW — from MessageView.createdAt()
        String role,
        int round,
        String entryType,
        String content) {}
```

`ConversationFold` methods gain corresponding parameters.
`ConversationProjection.doApply()` passes `message.sender()` and
`message.createdAt()` through to all fold methods that create
`ThreadEntry` instances.

All existing tests update to pass the new fields. No behavioural change.

---

## 2. Common Ground Types

### CommonGroundState

The derived view — partitions all conversation points by epistemic status:

```java
public record CommonGroundState(
        Map<String, GroundedFact> establishedFacts,
        Map<String, GroundedFact> pendingClaims,
        Map<String, GroundedFact> disputedPoints) {}
```

### GroundedFact

Epistemic metadata per point — extracts only what common ground cares
about, not the full thread:

```java
public record GroundedFact(
        String pointId,
        String topic,
        EpistemicStatus status,
        String content,
        Set<String> acknowledgedBy,
        Set<String> disputedBy,
        int round) {}
```

`round` is the round of the point's first thread entry — i.e., when the
claim was first raised. This is deterministic and does not change as the
point receives responses. Rules that need recency use
`ParticipantContext.roundsSinceLastActivity`, not `GroundedFact.round`.

`acknowledgedBy` is populated from `ParticipantContext.acknowledgedBy`
(RESPONSE and DONE senders only — not STATUS, HANDOFF, or other response
types). `disputedBy` is the union of `ParticipantContext.disputedBy`
(DECLINE senders) and `ParticipantContext.failedBy` (FAILURE senders).
Both indicate the point is contested; the distinction between active
refusal and failed obligation is available in the `ParticipantContext` for
rules that need it, but the `GroundedFact` summarises both as "disputed"
for rendering purposes.

### EpistemicStatus

```java
public enum EpistemicStatus {
    ESTABLISHED,
    PENDING,
    DISPUTED
}
```

### CommonGroundAnalyser

Stateless utility — classifies each point using the provided epistemic
rule:

```java
public final class CommonGroundAnalyser {
    private CommonGroundAnalyser() {}

    public static CommonGroundState analyse(ConversationState state,
                                            EpistemicRule rule) { ... }
}
```

For each `ConversationPoint`, the analyser:
1. Builds a `ParticipantContext` from the point's thread entries (using
   sender, messageType, round)
2. Calls `rule.classify(point, context)` to get the `EpistemicStatus`
3. Creates a `GroundedFact` and partitions into the appropriate map

---

## 3. Epistemic Rules

### The strategy interface

```java
@FunctionalInterface
public interface EpistemicRule {
    EpistemicStatus classify(ConversationPoint point,
                             ParticipantContext context);
}
```

### ParticipantContext

Pre-computed by the analyser from thread entries — the rule doesn't parse
threads:

```java
public record ParticipantContext(
        Set<String> allParticipants,
        Set<String> respondedBy,
        Set<String> acknowledgedBy,
        Set<String> completedBy,
        Set<String> disputedBy,
        Set<String> failedBy,
        int roundsSinceLastActivity) {}
```

- `allParticipants` — all senders who appear in any thread entry for
  this point, including the point initiator. Superset of all other
  sender sets. Useful for quorum-based rules (e.g., "all participants
  have acknowledged").
- `respondedBy` — all senders who sent any agent-visible response
  (RESPONSE, DONE, STATUS, HANDOFF, FAILURE, DECLINE). Proves awareness
  of the point.
- `acknowledgedBy` — senders who sent RESPONSE or DONE only. These are
  the message types that indicate epistemic engagement with the point's
  content (an explicit answer) or fulfilment of the obligation (action
  completed).
- `completedBy` — senders who sent DONE specifically. Subset of
  `acknowledgedBy`. Required by `commitmentResolution` to distinguish
  obligation fulfilment from mere acknowledgement.
- `disputedBy` — senders who sent DECLINE
- `failedBy` — senders who sent FAILURE
- `roundsSinceLastActivity` — difference between the conversation's max
  round (derived from the highest round across all thread entries in the
  state) and this point's last thread entry round

STATUS (progress report) and HANDOFF (delegation) are in `respondedBy`
but not `acknowledgedBy` — they prove the agent saw the point but do not
indicate agreement with its content. FAILURE is neither acknowledgement
nor dispute — it's a separate category (`failedBy`) indicating a failed
obligation.

### Provided rules

**`EpistemicRules.explicitAcknowledgement(int minParticipants)`**

ESTABLISHED when `acknowledgedBy.size() >= minParticipants`. DISPUTED if
any DECLINE. PENDING otherwise. Clinical use case — explicit per-agent
sign-off.

**`EpistemicRules.tacitAcceptance(int windowRounds)`**

ESTABLISHED when `respondedBy.size() >= 1` (at least one agent has seen
the point) AND `roundsSinceLastActivity >= windowRounds` AND `disputedBy`
is empty AND `failedBy` is empty. A point with zero responses cannot be
tacitly accepted — silence from agents who never saw a claim is not
acceptance. FAILURE also blocks tacit acceptance since a failed obligation
is an unresolved state. Drafthouse use case — absence of objection =
acceptance, but only after demonstrated awareness.

**`EpistemicRules.commitmentResolution()`**

ESTABLISHED when `completedBy` is non-empty (obligation fulfilled by at
least one sender). DISPUTED when `disputedBy` or `failedBy` is non-empty.
PENDING otherwise. Uses `completedBy` (not `acknowledgedBy`) because a
RESPONSE is acknowledgement but not completion — only DONE confirms the
obligation was met. Compliance use case — only completed commands are
facts.

### Composition

Rules compose via default methods on `EpistemicRule`:

- **`and()`** — returns the most conservative status
  (DISPUTED > PENDING > ESTABLISHED)
- **`or()`** — returns the most permissive

```java
EpistemicRules.commitmentResolution()
    .or(EpistemicRules.explicitAcknowledgement(2))
```

ESTABLISHED if the commitment was fulfilled OR at least 2 participants
acknowledged.

**Choosing `and()` vs `or()`:** `and()` requires all constituent rules to
agree — use it when multiple independent conditions must all hold (e.g.,
commitment completed AND explicitly acknowledged, for compliance). `or()`
establishes when any single rule is satisfied — use it when rules
represent alternative sufficient conditions (e.g., either tacit acceptance
OR explicit acknowledgement). If a composed rule produces counterintuitive
results, the combinator choice is usually wrong, not the individual rules.

---

## 4. Convergence Types

### ConvergenceSignal

```java
public record ConvergenceSignal(
        ConvergenceState state,
        double confidence,
        String reason) {}
```

### ConvergenceState

```java
public enum ConvergenceState {
    PROGRESSING,
    CONVERGING,
    CONSENSUS,
    DEADLOCK,
    DIMINISHING_RETURNS
}
```

| State | Supervisor action |
|-------|------------------|
| PROGRESSING | Continue normally |
| CONVERGING | Let current round finish, don't inject new topics |
| CONSENSUS | Close the conversation |
| DEADLOCK | Introduce tiebreaker or escalate |
| DIMINISHING_RETURNS | Close with partial result or escalate |

`origin` (0.0–1.0) lets the supervisor set its own threshold — a 0.6
CONSENSUS might continue, a 0.95 CONSENSUS closes immediately.

### ConvergenceAnalyser

Takes `CommonGroundState` as input — the dependency chain:

```java
public final class ConvergenceAnalyser {
    private ConvergenceAnalyser() {}

    public static ConvergenceSignal analyse(ConversationState state,
                                            CommonGroundState commonGround,
                                            ConvergencePolicy policy,
                                            int recentWindow) { ... }
}
```

`recentWindow` controls how many recent messages are used when building
`ConvergenceContext` (for similarity and length trend calculations).
Policies that need a different window can compute from the raw
`ConversationState` they receive — the context is a convenience, not a
constraint.

### Integration with TerminationCondition

`ConvergencePolicy` is a signal producer — it returns a rich
`ConvergenceSignal` with state classification and confidence.
`TerminationCondition<T>` is the platform's termination SPI used by
execution drivers (`OrchestratedDriver`, `ChoreographedDriver`) and
pattern builders (notably `DebateBuilder.convergence()`). These are
deliberately separate abstractions: the policy analyses, the condition
decides.

A bridge adapter connects them:

```java
public class ConvergenceTermination<T> implements TerminationCondition<T> {

    private final Function<T, ConversationState> stateExtractor;
    private final EpistemicRule epistemicRule;
    private final ConvergencePolicy policy;
    private final int recentWindow;
    private final double confidenceThreshold;
    private final Set<ConvergenceState> terminateOn;

    @Override
    public Uni<TerminationDecision> evaluate(TerminationContext<T> context) {
        ConversationState state = stateExtractor.apply(context.state());
        CommonGroundState cg = CommonGroundAnalyser.analyse(state, epistemicRule);
        ConvergenceSignal signal = ConvergenceAnalyser.analyse(
                state, cg, policy, recentWindow);

        if (terminateOn.contains(signal.state())
                && signal.confidence() >= confidenceThreshold) {
            return Uni.createFrom().item(toDecision(signal));
        }
        return Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
    }
}
```

The mapping: CONSENSUS → `Complete`, DEADLOCK → `Escalate`,
DIMINISHING_RETURNS → `Complete` (with partial result reason). The
`stateExtractor` function allows the adapter to work with any execution
state type that contains a `ConversationState`.

Usage with `DebateBuilder`:

```java
Patterns.debate()
    .debaters(agentA, agentB)
    .convergence(new ConvergenceTermination<>(
        MyState::conversationState,
        EpistemicRules.tacitAcceptance(2),
        ConvergencePolicies.structural(0.8, 3),
        5, 0.7,
        Set.of(CONSENSUS, DEADLOCK)))
    .build();
```

---

## 5. Convergence Policies

### The strategy interface

```java
@FunctionalInterface
public interface ConvergencePolicy {
    ConvergenceSignal evaluate(ConversationState state,
                               CommonGroundState commonGround,
                               ConvergenceContext context);
}
```

### ConvergenceContext

Pre-computed raw indicators — policies don't reparse threads:

```java
public record ConvergenceContext(
        int totalPoints,
        int establishedCount,
        int pendingCount,
        int disputedCount,
        double recentSimilarity,
        double messageLengthTrend,
        int roundsSinceNewPoint,
        int roundsSinceStatusChange,
        Map<MessageType, Integer> recentMessageTypeCounts) {}
```

- `recentSimilarity` — average pairwise Jaccard similarity between
  consecutive entries in the recent window. Algorithm: flatten all thread
  entries across all points, sort by `createdAt`, take the last
  `recentWindow` entries, tokenize each entry's content by whitespace,
  compute Jaccard similarity between each consecutive pair's token sets,
  return the mean. High values indicate repetitive messaging (agents
  making the same points).
- `messageLengthTrend` — ratio of recent avg content length to overall
  avg (< 1.0 = messages shrinking)
- `roundsSinceNewPoint` — rounds since any new point was initiated
- `roundsSinceStatusChange` — rounds since any point changed epistemic
  status in the common ground

### Provided policies

**`ConvergencePolicies.structural(double similarityThreshold, int staleRounds)`**

Multi-signal heuristic:
- `recentSimilarity >= similarityThreshold` → DEADLOCK (agents repeating
  themselves)
- `roundsSinceNewPoint >= staleRounds` → DIMINISHING_RETURNS (no new
  ideas emerging)
- `recentMessageTypeCounts` ratio of STATUS to substantive types
  (RESPONSE, COMMAND, QUERY) exceeding 2:1 → DIMINISHING_RETURNS (agents
  reporting status more than contributing substance)
- `established / total >= 0.9` → CONSENSUS (near-complete agreement)
- `established / total >= 0.5` AND `roundsSinceStatusChange <= 2` →
  CONVERGING (more than half established and still actively resolving
  points — don't inject new topics)
- Otherwise → PROGRESSING (conversation is active but not yet converging)

Pure heuristic, no LLM.

**`ConvergencePolicies.commonGroundRatio(double consensusThreshold, double deadlockDisputeRatio)`**

Consensus when `established / total >= consensusThreshold`. Deadlock when
`disputed / total >= deadlockDisputeRatio`. Simpler — only looks at
common ground ratios, ignores content similarity.

**`ConvergencePolicies.composite(ConvergencePolicy... policies)`**

Evaluates all policies, returns the signal with highest confidence. When
confidence is tied, the state with the most severe supervisor action wins.
Tiebreaking order: DEADLOCK > DIMINISHING_RETURNS > CONVERGING >
PROGRESSING > CONSENSUS. Rationale: a premature close (false CONSENSUS)
or missed deadlock is worse than continuing extra rounds, so ambiguity
resolves toward action-requiring states.

Allows combining structural heuristics with common-ground-ratio checks.

### Relationship to existing convergence code

- **`JudgeConvergence`** (agentic/termination) — LLM-delegated,
  per-round, expensive. Stays where it is. These policies are the
  heuristic complement: cheap, structural, run continuously.
- **Qhorus watchdog** `ECHO_CHAMBER` / `LOOP_DETECTED` — pathology
  detection (bad). Convergence detection is natural completion (good).
  Different signals, but `recentSimilarity` uses the same Jaccard
  mechanic. We implement our own — qhorus's `JaccardSimilarity` is
  package-private and trivial.

---

## 6. Rendering Integration

Common ground and convergence follow the same render-time supplementary
input pattern as reactions — passed to the renderer, not baked into state.

### RenderContext

Supplementary render-time inputs bundled into a single record, replacing
the overload-per-combination pattern:

```java
public record RenderContext(
        Map<Long, List<ReactionGroup>> reactions,
        CommonGroundState commonGround,
        ConvergenceSignal convergence) {

    public static final RenderContext EMPTY =
            new RenderContext(Map.of(), null, null);

    public static RenderContext withReactions(
            Map<Long, List<ReactionGroup>> reactions) {
        return new RenderContext(reactions, null, null);
    }
}
```

### Updated renderer API

```java
public String render(ConversationState state)                    // convenience, delegates below
public String render(ConversationState state, RenderContext ctx) // single method for all combinations
```

The existing `render(state, reactions)` overload is replaced. Callers
migrate to `render(state, RenderContext.withReactions(reactions))`.
This scales as new supplementary inputs are added without combinatorial
explosion.

### Common ground rendering

When present, each point gets an epistemic badge after the point header:

- ESTABLISHED: `[established by agent-a, agent-b]`
- PENDING: `[pending — awaiting acknowledgement]`
- DISPUTED: `[disputed by agent-c]`

In `groupByTopic` mode, each topic section gets a summary line:
"3 established, 1 pending, 1 disputed".

### Convergence rendering

A single line at the top of the output:

```
**Convergence:** CONVERGING (0.78) — common ground growing, 1 disputed point remaining
```

Conversation-level signal, not per-point.

### Config additions

```java
boolean showEpistemicStatus       // default false
boolean showConvergenceSignal     // default false
```

Both default false — existing consumers see no change.

---

## 7. Package Structure

All new types in `io.casehub.blocks.conversation`:

```
conversation/
  ├── ConversationFold.java          (modified — sender/createdAt params)
  ├── ConversationProjection.java    (modified — passes sender/createdAt)
  ├── ConversationRenderer.java      (modified — RenderContext API)
  ├── ConversationRendererConfig.java (modified — two new config fields)
  ├── RenderContext.java             (NEW)
  ├── ConversationState.java         (unchanged)
  ├── ConversationPoint.java         (unchanged)
  ├── ThreadEntry.java               (modified — sender, createdAt fields)
  ├── CommonGroundAnalyser.java      (NEW)
  ├── CommonGroundState.java         (NEW)
  ├── GroundedFact.java              (NEW)
  ├── EpistemicStatus.java           (NEW)
  ├── EpistemicRule.java             (NEW)
  ├── EpistemicRules.java            (NEW)
  ├── ParticipantContext.java        (NEW)
  ├── ConvergenceAnalyser.java       (NEW)
  ├── ConvergenceSignal.java         (NEW)
  ├── ConvergenceState.java          (NEW)
  ├── ConvergencePolicy.java         (NEW)
  ├── ConvergencePolicies.java       (NEW)
  └── ConvergenceContext.java        (NEW)
```

Additionally, in `io.casehub.blocks.agentic.termination`:

```
termination/
  ├── ConvergenceTermination.java    (NEW — bridges ConvergencePolicy → TerminationCondition)
```

No sub-packages within conversation — the conversation package is
cohesive (all types participate in the same fold → derive → render
pipeline). 13 new files, 4 modified.

---

## 8. Dependencies

No new dependencies. All compile-scope dependencies already exist:

- `casehub-qhorus-api` — `MessageView`, `MessageType`, `ReactionGroup`
- `java.time.Instant` — standard library

No CDI. No Quarkus. Pure Java, matching the existing conversation package
pattern.

---

## 9. Impact on Consumers

All changes are additive. Existing consumers are unaffected:

| Consumer | Impact |
|----------|--------|
| drafthouse | `ConversationProjection` subclass gains sender/createdAt on thread entries automatically. Can opt into common ground analysis with domain-specific `EpistemicRule`. |
| devtown | Same as drafthouse. Code review might use `commitmentResolution()` for review point tracking. |
| clinical | Could use `explicitAcknowledgement(2)` requiring both attending and reviewing physician. |
| AML | Could use `commitmentResolution().and(explicitAcknowledgement(1))` for compliance-grade fact establishment. |

No consumer changes required — new features are opt-in via analyser
calls and renderer config.

---

## 10. Test Strategy

### ThreadEntry enrichment

In existing `ConversationFoldTest`, `ConversationProjectionTest`:
- Verify `sender` and `createdAt` propagate through fold methods
- Existing tests update to pass new fields — no logic change

### CommonGroundAnalyser

`CommonGroundAnalyserTest` — build `ConversationState` directly:
- Each epistemic rule tested in isolation (ack thresholds, DECLINE →
  DISPUTED, DONE → ESTABLISHED)
- Rule composition: `and()` returns most conservative, `or()` returns
  most permissive
- Edge cases: empty state, single point with no responses, point with
  only infrastructure entries
- **Critical edge case:** `tacitAcceptance` must NOT establish a point
  with zero responses — verify that the `respondedBy.size() >= 1`
  precondition correctly prevents false positives when no agent has
  ever seen the claim
- FAILURE blocks tacit acceptance — point with only FAILURE responses
  remains PENDING, not ESTABLISHED
- HANDOFF and STATUS in `respondedBy` but not `acknowledgedBy` —
  verify that `explicitAcknowledgement` requires RESPONSE/DONE

### ConvergenceAnalyser

`ConvergenceAnalyserTest` — build `ConversationState` + `CommonGroundState`:
- `structural` policy: high similarity → DEADLOCK, declining length →
  DIMINISHING_RETURNS, high established ratio → CONSENSUS
- `commonGroundRatio` policy: threshold-based detection
- `composite`: highest-confidence signal wins
- Edge cases: empty conversation, single round, all established from
  round 1

### Integration

`CommonGroundConvergenceIntegrationTest` — full pipeline:
- Fold → derive common ground → derive convergence → render
- Scenario: debate evolving PENDING → ESTABLISHED → CONSENSUS signal
- Scenario: hardening positions with DECLINEs → DEADLOCK signal
- Scenario: diminishing returns — substantive early, thin later

All tests plain JUnit 5 + AssertJ + Mockito. No CDI, no Quarkus.
