# Gate Rejection Outcome Recording — Design Spec

**Issue:** casehubio/blocks#36
**Date:** 2026-07-09
**Status:** Approved

## Problem

When a worker's `PlannedAction` is gate-rejected or gate-expired, no routing
outcome is recorded in the CBR store. Workers that consistently produce
risky/rejected actions accumulate no negative feedback — their CBR history
shows only past successes or nothing, creating a silent positive bias.

### Root cause

`PendingActionGate` drops routing context (`bindingName`, `capabilityName`) at
gate-open time. Neither `ActionGateRejectedHandler` nor
`ActionGateExpiredHandler` injects `RoutingOutcomeRecorder`. The data flow gap
makes recording impossible at gate-resolution time.

### Current recording coverage

| Path | Recorded? | Outcome |
|------|-----------|---------|
| Worker SUCCESS (no PlannedAction) | Yes | `"SUCCESS"` |
| Worker Declined/Failed/Expired | Yes | `"FAILURE"` |
| Gate approved → re-dispatch → SUCCESS | Yes | `"SUCCESS"` |
| Gate **rejected** | **No** | — |
| Gate **expired** | **No** | — |

Additionally, the approved re-dispatch path passes `bindingName=null`, forcing
a lossy fuzzy-match fallback in `extractCapabilityTag`.

### Garden context

- **GE-20260706-56a75c**: documents the misleading `WorkerOutcomeResolvedEvent`
  naming — this design correctly avoids that event and records directly through
  the `RoutingOutcomeRecorder` SPI.

## Design

Four changes across engine-api, engine (runtime + common), and blocks.

### 1. SPI changes (engine-api)

New enum in `io.casehub.api.spi.routing`:

```java
public enum RoutingOutcome {
    SUCCESS,
    FAILURE,
    GATE_REJECTED,
    GATE_EXPIRED
}
```

`RoutingOutcomeRecorder.record()` signature changes:

```java
// Before
Uni<Void> record(AgentRoutingContext context, String workerId,
                 String bindingName, String executionOutcome,
                 @Nullable Duration executionDuration);

// After
Uni<Void> record(AgentRoutingContext context, String workerId,
                 String bindingName, RoutingOutcome outcome,
                 @Nullable Duration executionDuration);
```

Javadoc updates to document all four outcomes and their semantics:
- `SUCCESS` — worker completed successfully (including gate-approved re-dispatch)
- `FAILURE` — worker returned non-success outcome (Declined/Failed/Expired)
- `GATE_REJECTED` — worker's planned action was rejected by a human via oversight gate
- `GATE_EXPIRED` — worker's planned action gate expired without review

The Javadoc also updates to reflect that gate-rejected and gate-expired outcomes
are recorded directly from gate resolution handlers, not through the worker
completion path. The existing note about approved gates re-dispatching stays.

`ExperiencePlanStep.stepOutcome` Javadoc updates to enumerate all `RoutingOutcome`
values — the current "SUCCESS, FAILURE, DECLINED, etc." leaves the value space
unspecified for consumers. The `@param stepOutcome` description changes to
reference `RoutingOutcome` enum values as the canonical vocabulary, and replaces
"the worker's outcome" with "the routing outcome" to reflect that gate outcomes
are not worker outcomes.

### 2. Data model change (engine-common)

`PendingActionGate` gains two nullable fields:

```java
public record PendingActionGate(
    long gateId,
    String workerId,
    String idempotency,
    Map<String, Object> deferredOutput,
    PlannedAction plannedAction,
    @Nullable String bindingName,
    @Nullable String capabilityName) {}
```

Both nullable because `extractCapabilityTag` can return null when no capability
binding exists for the worker. Gate handlers skip recording when
`capabilityName` is null — consistent with `fireOutcomeRecorder`'s existing
guard.

`WorkflowExecutionCompletedHandler.handleGate()` computes `capabilityName` via
`extractCapabilityTag(caseInstance, worker, event.bindingName())` and passes
both to the constructor.

### 3. Engine runtime changes

**ActionGateRejectedHandler:**
- Inject `Instance<RoutingOutcomeRecorder>`
- Capture context snapshot (`caseInstance.getCaseContext().snapshot().asJsonNode()`)
  at the top of `onActionGateRejected()`, BEFORE gate clearance and signal writes
  — consistent with `fireOutcomeRecorder`'s pre-modification snapshot pattern
  on both the success path (`contextBefore` before output application) and the
  failure path (snapshot before `_outcomes` modifications)
- After worker status notification, before EventLog write:
  - Guard: skip if recorder unsatisfied or `gate.capabilityName()` null
  - Construct `AgentRoutingContext` using the pre-captured context snapshot
  - Call `record()` with `RoutingOutcome.GATE_REJECTED`, null duration
  - Subscribe fire-and-forget with warn-on-failure

**ActionGateExpiredHandler:**
- Identical pattern (including pre-mutation context snapshot), using
  `RoutingOutcome.GATE_EXPIRED`

**ActionGateApprovedHandler:**
- `refireCompletion()` passes `gate.bindingName()` instead of `null` in the
  `WorkflowExecutionCompleted.approved()` call — exact binding match instead of
  lossy fuzzy fallback

**WorkflowExecutionCompletedHandler:**
- `fireOutcomeRecorder()` parameter changes from `String outcomeString` to
  `RoutingOutcome outcome`
- Call sites change from `"SUCCESS"`/`"FAILURE"` literals to enum values

### 4. Blocks changes

**CbrRoutingOutcomeRecorder:**
- `record()` parameter type changes from `String executionOutcome` to
  `RoutingOutcome outcome`
- `PlanTrace` construction uses `outcome.name()` for the string value
- `PlanCbrCase` construction uses `outcome.name()` for the outcome field

**CbrAgentRoutingStrategy.analyseByType():**
- No change needed. `"SUCCESS".equals(trace.stepOutcome())` still works —
  `RoutingOutcome.SUCCESS.name()` produces `"SUCCESS"`, and gate outcomes
  produce `"GATE_REJECTED"`/`"GATE_EXPIRED"` which are not `"SUCCESS"`,
  correctly counting against the worker's success rate.

**CbrRoutingPromptSection.format():**
- Summary line changes from binary `SUCCESS`/`FAILURE` to per-outcome counts.
  Currently, `failures = total - successes` labels all non-SUCCESS outcomes as
  "FAILURE" — with gate outcomes stored, a worker with 1 SUCCESS and 1
  GATE_REJECTED would appear as "1 SUCCESS, 1 FAILURE", contradicting the
  detail lines which show the raw outcome
- Track per-outcome distribution instead of just `[successes, total]`
- Format: `"agent": N cases — X SUCCESS, Y FAILURE, Z GATE_REJECTED (P% success)`
- Case detail lines already render `trace.stepOutcome()` verbatim — no change
  needed there

**CbrRoutingOutcomeRecorderTest:**
- Update to pass `RoutingOutcome` values instead of strings
- Add test confirming `GATE_REJECTED` records are stored

**CbrRoutingPromptSectionTest:**
- Add test with mixed outcomes (SUCCESS + GATE_REJECTED + GATE_EXPIRED):
  verify summary line renders per-outcome counts (`1 SUCCESS, 1 GATE_REJECTED`)
  and does NOT label gate outcomes as "FAILURE"
- Add test verifying summary-detail consistency: summary shows GATE_REJECTED
  count, detail line shows `GATE_REJECTED` outcome — no contradiction

## Protocol coherence

- **module-tier-structure**: `RoutingOutcome` enum is pure-Java vocabulary
  colocated with the SPI in `api/spi/routing/` — no framework dependencies,
  consistent with the three-tier module structure (pure-Java SPI tier).
- `RoutingOutcomeRecorder` injection model unchanged — remains a single-bean
  `Instance<>` SPI with fire-and-forget subscription.

## What this does NOT change

- `PlanTrace.stepOutcome()` stays a String in neocortex-memory-api — it's a
  storage record, not a domain type
- `WorkerOutcomeResolvedEvent` is not involved — per GE-20260706-56a75c, it
  fires only for non-success outcomes and is not the correct integration point
- No new event bus addresses — gate handlers record directly through the
  existing SPI injection
- `CbrAgentRoutingStrategy.analyseByType()` success-rate logic unchanged — the
  String comparison against stored records is unaffected. Gate outcomes count
  uniformly against the worker's success rate: a worker whose actions
  consistently trigger gate rejection is not producing deployable outcomes,
  regardless of the reason. This is an intentional design choice — future
  weighted outcome differentiation is tracked as casehubio/blocks#43

## Repos and modules affected

| Repo | Module | Changes |
|------|--------|---------|
| engine | api | `RoutingOutcome` enum, `RoutingOutcomeRecorder` signature |
| engine | common | `PendingActionGate` record extension |
| engine | runtime | Gate handlers + completion handler updates |
| blocks | — | `CbrRoutingOutcomeRecorder`, `CbrRoutingPromptSection` + tests |
