# Trust Attestation SPIs + FewShotOptimiser Diversity Selection

**Issues:** #78, #79, #80, #81
**Date:** 2026-08-05
**Status:** Reviewed

## Overview

Four enhancements on a single branch: three trust SPIs extracted from domain repo duplication (#79, #80, #81) and one prompt optimiser enhancement (#78). The trust SPIs compose with the existing `AttestationIntent`/`AttestationIntentWriter` in blocks. The diversity enhancement is independent.

---

## 1. Attestation Package (#79)

**Package:** `io.casehub.blocks.attestation`

### 1.1 Existing Types (commit + modify)

**`AttestationIntent`** — existing untracked record in `src/main/java/io/casehub/blocks/attestation/`. Add one field:

- `@Nullable UUID causedByEntryId` — source event entry ID for idempotent writes. Nullable because not all attestation sources have a causal entry. Idempotency semantics (no-op vs upsert on duplicate `causedByEntryId`) are implementation-defined by each `AttestationIntentWriter`.

Full shape after modification:

```java
public record AttestationIntent(
        UUID entryId,
        UUID subjectId,
        AttestationVerdict verdict,
        double confidence,
        String capabilityTag,
        String attestorId,
        ActorType actorType,
        String attestorRole,
        Map<String, Double> dimensions,
        String evidence,
        UUID namespace,
        @Nullable UUID causedByEntryId) {}
```

**`AttestationIntentWriter`** — existing untracked SPI. Unchanged. Implementations MUST persist the intent using the provided `entryId` — callers (including `VouchService`) pre-generate the ID and return it to their own callers for audit trails.

```java
public interface AttestationIntentWriter {
    void write(AttestationIntent intent, String tenancyId);
}
```

### 1.2 LifecycleAttestationObserver (#79)

**`LifecycleAttestationObserver<E>`** — `@FunctionalInterface` SPI. Domain repos implement this to map lifecycle events to attestation intents. Return value is non-null (per jspecify defaults) — return `List.of()` when the event produces no attestations. One event may produce multiple intents (e.g., a PR merge creates both a merge-rate and first-attempt-quality attestation).

```java
@FunctionalInterface
public interface LifecycleAttestationObserver<E> {
    List<AttestationIntent> observe(E event, AttestationContext context);
}
```

**`AttestationContext`** — ambient context the observer needs:

```java
public record AttestationContext(
        String tenancyId,
        UUID caseId,
        String capabilityTag) {}
```

**No chaining.** Lifecycle observers are typed per event — `LifecycleAttestationObserver<PrLifecycleEvent>` is a different type from `LifecycleAttestationObserver<WorkItemOutcomeEvent>`. CDI dispatches by type parameter. This is unlike `ActionRiskClassifier` where multiple classifiers compete on the same action.

**Consumer protocol:** The caller invokes the observer, then feeds each intent to `AttestationIntentWriter.write()`. On partial write failure (intent K of N throws), intents K+1..N are lost — callers that require atomicity must wrap the loop in a transaction. Retry is safe when `causedByEntryId` is set and the writer implements idempotent semantics.

---

## 2. Trust SPIs (#80, #81)

**Package:** `io.casehub.blocks.trust`

Separate from `blocks.attestation` — these SPIs have no compile-time dependency on attestation types. They orbit the trust lifecycle but their inputs and outputs are self-contained.

### 2.1 IntakeClassifier (#80)

**`IntakeClassifier<S>`** — `@FunctionalInterface` SPI for classifying subjects into intake lanes.

```java
@FunctionalInterface
public interface IntakeClassifier<S> {
    IntakeResult classify(S subject, IntakeContext context);
}
```

**`IntakeContext`** — lightweight record the consumer populates:

```java
public record IntakeContext(
        String tenancyId,
        @Nullable String capabilityTag,
        Map<String, Object> attributes) {

    public IntakeContext(String tenancyId) {
        this(tenancyId, null, Map.of());
    }
}
```

`attributes` is the escape hatch — consumers put trust scores, observation counts, or any other signal here. The SPI does not prescribe what the classification signal is.

**`IntakeResult`** — classification output:

```java
public record IntakeResult(
        String lane,
        double confidence,
        String reason,
        Map<String, Object> metadata) {

    public IntakeResult(String lane, double confidence, String reason) {
        this(lane, confidence, reason, Map.of());
    }
}
```

`lane` is a string, not an enum — domains define their own lanes (devtown: FAST_TRACK/STANDARD/TRIAGE; clinical: URGENT/ROUTINE/DEFERRED). `origin` is [0.0, 1.0] (validated in compact constructor). `reason` is human-readable for audit trails.

**Deviation from issue #80:** The issue specifies `laneWeight`, `trustScore`, and `observationCount` as explicit fields. This spec drops them because `IntakeClassifier<S>` is a generic SPI — not all classification is trust-based. Trust-specific values belong in the `metadata` map: `metadata.put("trustScore", 0.85)`. This keeps the SPI contract domain-agnostic while preserving audit-trail capability for trust-based implementations.

### 2.2 VouchService (#81)

**`VouchConstraint`** — pluggable eligibility check SPI:

```java
public interface VouchConstraint {
    VouchEligibility check(VouchRequest request);
}
```

**`VouchEligibility`** — sealed result:

```java
public sealed interface VouchEligibility {
    record Eligible() implements VouchEligibility {}
    record Ineligible(String reason) implements VouchEligibility {}
}
```

**`VouchRequest`** — what the voucher is asking to do:

```java
public record VouchRequest(
        String voucherId,
        UUID voucheeId,
        String capabilityTag,
        String tenancyId,
        ActorType voucherActorType,
        String voucherRole,
        @Nullable UUID namespace,
        Map<String, Object> attributes) {}
```

**`VouchResult`** — sealed outcome:

```java
public sealed interface VouchResult {
    record Accepted(UUID attestationEntryId) implements VouchResult {}
    record Rejected(List<String> reasons) implements VouchResult {}
}
```

**`VouchService`** — orchestrator:

```java
public class VouchService {

    private final List<VouchConstraint> constraints;
    private final AttestationIntentWriter writer;

    public VouchService(List<VouchConstraint> constraints,
                        AttestationIntentWriter writer) { ... }

    public VouchResult vouch(VouchRequest request) {
        // 1. Run all constraints — collect Ineligible reasons
        // 2. If any Ineligible -> return Rejected(reasons)
        // 3. Build AttestationIntent (see field mapping below)
        // 4. Write via AttestationIntentWriter
        // 5. Return Accepted(entryId)
    }
}
```

**AttestationIntent field mapping for vouches:**

| AttestationIntent field | Source | Value |
|------------------------|--------|-------|
| `entryId` | Pre-generated | `UUID.randomUUID()` — returned in `Accepted` |
| `subjectId` | `request.voucheeId()` | Direct (both UUID) |
| `verdict` | Fixed | `AttestationVerdict.ENDORSED` |
| `origin` | Fixed | `1.0` |
| `capabilityTag` | `request.capabilityTag()` | Direct |
| `attestorId` | `request.voucherId()` | Direct |
| `actorType` | `request.voucherActorType()` | Direct |
| `attestorRole` | `request.voucherRole()` | Direct |
| `dimensions` | Fixed | `Map.of()` (endorsement has no dimensional scores) |
| `evidence` | Fixed | `"vouch"` |
| `namespace` | `request.namespace()` | Direct (nullable) |
| `causedByEntryId` | Fixed | `null` (vouches are not caused by prior entries) |

**Design decisions:**

- **Not `@ApplicationScoped`** — constructed by the consumer with domain-specific constraints. Issue #81 specifies `@ApplicationScoped`; this spec deviates deliberately. Vouching constraints are context-specific (devtown vouch has different rules from clinical vouch), not a global chain. The trade-off: consumers handle orchestration boilerplate, but get full control over constraint composition.
- **All-constraints-must-pass** — any `Ineligible` rejects. All rejection reasons collected for transparency.
- **TOCTOU limitation** — constraint checking and writing are not atomic. Two concurrent `vouch()` calls for the same voucher can both pass constraints and both write (e.g., exceeding a capacity limit). Consumers that require strict enforcement must provide transactional boundaries or use storage-layer constraints (unique indexes, optimistic locking).
- **No vouch revocation** — out of scope. Undo via CHALLENGED attestation against the same subject.
- **Risk propagation via EigenTrust** — the ENDORSED attestation creates a trust-graph edge. EigenTrust propagates reputation naturally. The vouch linkage (voucherId -> voucheeId in the attestation) is the data hook for future asymmetric weighting in ledger.

---

## 3. FewShotOptimiser Diversity (#78)

**Package:** `io.casehub.blocks.prompt` (SPI) + `io.casehub.blocks.prompt.optimiser` (implementations)

### 3.1 DiversityStrategy SPI

```java
package io.casehub.blocks.prompt;

@FunctionalInterface
public interface DiversityStrategy {
    List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples);
}
```

**Contract invariants:**
- Returned list MUST be a subset of the input (no new candidates created)
- Returned list size MUST be ≤ `maxExamples`
- Implementations MUST NOT mutate the input list
- Implementations MUST produce well-defined results for all inputs, including candidates with empty `input`/`output` strings
- If `shortlist.size() <= maxExamples`, return the full shortlist

### 3.2 TopNDiversityStrategy (identity implementation)

```java
package io.casehub.blocks.prompt.optimiser;

public class TopNDiversityStrategy implements DiversityStrategy {
    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        return shortlist.stream().limit(maxExamples).toList();
    }
}
```

Pure relevance selection — returns the first `maxExamples` candidates from the pre-sorted shortlist. Used as the default when no diversity is needed.

### 3.3 OutcomeAwareDiversityStrategy

Outcome coverage + token-level Jaccard MMR:

```java
package io.casehub.blocks.prompt.optimiser;

public class OutcomeAwareDiversityStrategy implements DiversityStrategy {

    private final double diversityWeight; // lambda in [0, 1], validated in constructor

    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        // 1. Group by outcome (case-insensitive on ExampleCandidate.outcome)
        // 2. Seed: pick highest-scoring candidate from each outcome group (up to maxExamples)
        // 3. Fill remaining slots via MMR:
        //    score = (1 - lambda) * relevance - lambda * maxSimilarity(candidate, selected)
        //    where relevance = qualityScore * similarityScore
        //    and similarity = Jaccard on whitespace-tokenised (input + " " + output)
        // 4. Return selected list ordered by original relevance score
    }
}
```

`diversityWeight` is validated in the constructor: `[0.0, 1.0]`. At 0.0: pure relevance (equivalent to `TopNDiversityStrategy`). At 1.0: maximises diversity.

**Jaccard similarity** — hand-written, ~10 lines. Whitespace-tokenised set intersection over union. Returns 0.0 when both token sets are empty. No third-party NLP dependency.

### 3.4 OptimiserConfig — No Change

`OptimiserConfig` is unchanged. `diversityWeight` is strategy-specific — it belongs on `OutcomeAwareDiversityStrategy`'s constructor, not in the shared config passed to every `PromptOptimiser`.

### 3.5 FewShotOptimiser Changes

1. Add `DiversityStrategy` constructor parameter. No-arg constructor defaults to `new TopNDiversityStrategy()`.
2. Take 2x `maxExamples` from existing score-and-filter pipeline (instead of capping at `maxExamples`)
3. Pass shortlist to `DiversityStrategy.select(shortlist, maxExamples)`
4. Map result to `FewShotExample` as before

No null branch — the optimiser always delegates to a strategy.

---

## Testing Strategy

### Attestation package

- `LifecycleAttestationObserverTest` — verify observer contract: event in, intents out. Test with a stub observer that maps a test event to known intents. Verify non-null return contract.
- `AttestationIntentTest` — verify causedByEntryId is nullable and carried through

### Trust SPIs

- `IntakeClassifierTest` — verify classification contract: subject + context in, result out. Test with a stub classifier. Verify compact constructor defaults. Verify confidence validation [0, 1].
- `VouchServiceTest` — verify orchestration:
  - All constraints pass -> ENDORSED attestation written, Accepted returned with pre-generated entryId
  - Any constraint fails -> no write, Rejected with all reasons
  - Multiple constraints fail -> all reasons collected
  - Verify AttestationIntent field mapping matches the table above
  - Writer exception propagates (not wrapped in VouchResult)

### Diversity selection

- `TopNDiversityStrategyTest`:
  - Returns first N candidates unchanged
  - Shortlist smaller than maxExamples: returns all
  - Empty shortlist: returns empty
- `OutcomeAwareDiversityStrategyTest`:
  - Homogeneous outcomes: verify seed phase picks from available categories
  - MMR fill: verify textually similar candidates are penalised
  - diversityWeight=0.0: degenerates to pure relevance ranking
  - diversityWeight=1.0: maximises diversity
  - Empty input/output strings: no NaN, no crash
  - Case-insensitive outcome grouping
  - Constructor rejects diversityWeight outside [0, 1]
- `FewShotOptimiserTest` updates:
  - Verify 2x shortlist passed to strategy
  - No-arg constructor uses TopNDiversityStrategy (backward compatible — identical behaviour)
  - Integration test: end-to-end with OutcomeAwareDiversityStrategy

---

## Dependencies

No new compile dependencies. All types used:
- `AttestationVerdict` — already a provided dependency via `casehub-ledger-api`
- `ActorType` — already a provided dependency via `casehub-platform-api`
- `@Nullable` — already a compile dependency via `org.jspecify:jspecify`

---

## Scope Exclusions

- No CDI discovery/chaining for `LifecycleAttestationObserver` — typed per event, no chain needed
- No vouch revocation — use CHALLENGED attestation
- No risk propagation logic — EigenTrust handles via trust graph
- No embedding vectors on `ExampleCandidate` — Jaccard on text is sufficient for the default
- No new provided dependencies
- No changes to `OptimiserConfig` — diversity parameters stay on strategy constructors
