# Cognition Scheduler SPI — Design Spec

**Issue:** casehubio/blocks#161
**Date:** 2026-09-16
**Branch:** issue-161-innerlife-scheduler-spi

## Overview

Formalise the implicit tick ordering in `CognitionCore` as a phase model,
remove InnerLife's redundant drive re-tick with a runtime-enforced ordering
contract, fix the data staleness in drive ordering, and expose an open
participation SPI for consumers to register custom tick participants.

## Problems Addressed

1. **Ordering hardcoded** — `CognitionCore.tick()` has the orchestrator
   sequence baked into imperative code. Adding or reordering requires
   editing the method body.
2. **InnerLife redundant re-tick** — `InnerLifeOrchestrator.doTick()`
   line 97 calls `driveOrchestrator.tick()` defensively. This is
   redundant when CognitionCore has already ticked drives, but
   necessary because there's no enforcement that drives ARE current.
3. **Drive ordering bug** — drives tick at position 4 (before strategy,
   userModel, mentalModel). 3 of 4 drive sources see one-tick-stale data.
   Contradicts #136 spec: "source orchestrators → DriveOrchestrator →
   GoalProposalOrchestrator."
4. **No consumer extension** — consumers cannot inject custom tick
   participants without modifying `CognitionCore`.

## Architecture

### Phase Enum

```java
public enum CognitionPhase {
    FOUNDATION,         // mood, memoryHygiene — no orchestrator deps
    SOURCE,             // narrative, strategy — populate caches from stores
    SOURCE_PER_SUBJECT, // userModel, mentalModel — per-subject iteration (special)
    DERIVED,            // drives — reads all source + foundation orchestrators
    TERMINAL            // goals — reads drives
}
```

Phases execute in enum declaration order. Within a phase, participants
execute in registration order (deterministic, insertion-ordered).

### Orchestrator-to-Phase Mapping

| Phase | Orchestrators | Dependencies |
|-------|--------------|-------------|
| FOUNDATION | MoodOrchestrator, MemoryHygieneOrchestrator | None (stores only) |
| SOURCE | NarrativeOrchestrator, StrategyLearningOrchestrator | Foundation stores |
| SOURCE_PER_SUBJECT | UserModelOrchestrator, MentalModelOrchestrator | Per-subject, iterated via SubjectResolver |
| DERIVED | DriveOrchestrator | mood, memoryHygiene, narrative, strategy, userModel, mentalModel |
| TERMINAL | GoalProposalOrchestrator | drives |
| (on-demand) | InnerLifeOrchestrator | drives (enforced via precondition) |

InnerLife is not a phase participant — it is invoked on-demand via
`evaluateProactive()` because it requires channel context that is not
available during scheduled ticks. See §InnerLife Integration.

### CognitionTickParticipant SPI

```java
@FunctionalInterface
public interface CognitionTickParticipant {
    void tick(CognitionTickContext context);
}
```

```java
public record CognitionTickContext(
    String agentId,
    String tenantId,
    @Nullable AgentDescriptor descriptor,
    SubjectResolver resolver
) {}
```

Single method, single context record. The context carries everything a
participant needs, including the `SubjectResolver` for participants
that need per-subject iteration internally. Built-in orchestrators are
called directly by CognitionCore with their specific signatures and
config checks — they are not wrapped in the SPI. The SPI exists for
custom participants only.

### CognitionCore Changes

#### New Field — InnerLifeOrchestrator

Add `@Nullable InnerLifeOrchestrator innerLife` as a constructor
parameter and field on CognitionCore. All three existing constructor
overloads gain this parameter (nullable — InnerLife is optional).

SocialAvatarCognition passes its `Optional<InnerLifeOrchestrator>` into
CognitionCore and removes its own separate `innerLife` field. InnerLife
access goes through `CognitionCore.innerLife()` accessor.

#### Participant Registration

```java
public class CognitionCore {
    private final Map<CognitionPhase, List<CognitionTickParticipant>> customParticipants;

    public void addParticipant(CognitionPhase phase, CognitionTickParticipant participant) { ... }
}
```

Registration is at construction time or early lifecycle (before the
first `tick()` call). Not thread-safe for concurrent registration —
consumers add participants during wiring, not mid-tick. Registration
is additive — custom participants run after built-in orchestrators
within their phase. `SOURCE_PER_SUBJECT` does not accept custom
participants (throws `IllegalArgumentException`). Consumers needing
per-subject custom ticking register at another phase and iterate
subjects internally via `context.resolver()`.

#### Tick Method

```java
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 SubjectResolver resolver) {
    var context = new CognitionTickContext(agentId, tenantId, descriptor, resolver);

    // FOUNDATION
    if (config.moodEnabled()) safeRun(() -> tickMood(agentId, tenantId));
    if (config.memoryHygieneEnabled() && memoryHygiene != null)
        safeRun(() -> memoryHygiene.tick(agentId, tenantId));
    runCustomParticipants(CognitionPhase.FOUNDATION, context);

    // SOURCE
    if (config.narrativeEnabled() && narrative != null)
        safeRun(() -> narrative.tick(agentId, tenantId));
    if (config.strategyEnabled() && strategy != null)
        safeRun(() -> strategy.tick(agentId, tenantId));
    runCustomParticipants(CognitionPhase.SOURCE, context);

    // SOURCE_PER_SUBJECT (no custom participants)
    for (String subjectId : resolver.relevantSubjects(agentId, tenantId)) {
        if (config.userModelEnabled() && userModel != null)
            safeRun(() -> userModel.tick(agentId, subjectId, tenantId));
        if (config.mentalModelEnabled() && mentalModel != null)
            safeRun(() -> mentalModel.tick(agentId, subjectId, tenantId));
    }

    // DERIVED
    if (config.drivesEnabled() && descriptor != null)
        safeRun(() -> drives.tick(agentId, tenantId, descriptor));
    runCustomParticipants(CognitionPhase.DERIVED, context);

    // TERMINAL
    if (config.goalsEnabled() && goals != null && descriptor != null)
        safeRun(() -> goals.tick(agentId, tenantId, descriptor));
    runCustomParticipants(CognitionPhase.TERMINAL, context);
}
```

Built-in orchestrators run first within each phase, then custom
participants. `CognitionConfig` flags gate built-in orchestrators only —
custom participants are always invoked (consumers manage their own
enable/disable logic).

**Note:** Mood's init-check (`mood.currentMood().isEmpty()` → `mood.record()`)
is currently unguarded by `safeRun()`. This spec wraps it via `tickMood()`
helper, changing error semantics — mood init failures are now caught and
logged instead of propagating. This is an intentional improvement.

#### Error Isolation

`safeRun()` wraps every participant call (built-in and custom) in
try/catch that logs and continues. This preserves current behaviour:
one participant failing does not crash the tick cycle. The isolation is
per-participant, not per-phase — a failing FOUNDATION participant does
not skip the remaining FOUNDATION participants.

### CognitionConfig Changes

Add `innerLifeEnabled` flag:

```java
public record CognitionConfig(
    boolean moodEnabled,
    boolean drivesEnabled,
    boolean mentalModelEnabled,
    boolean userModelEnabled,
    boolean strategyEnabled,
    boolean narrativeEnabled,
    boolean goalsEnabled,
    boolean memoryHygieneEnabled,
    boolean innerLifeEnabled,
    boolean directivePrompts
) { ... }
```

Update `all()`, `none()`, and `with()` switch to include the new field.
InnerLife's on-demand evaluation is gated by this flag — `evaluateProactive()`
returns null when `innerLifeEnabled=false`.

Custom participants are not subject to `CognitionConfig` flags. This is
the correct separation: config controls what blocks ships; consumers
control what they add.

### InnerLife Integration

#### Why Not TERMINAL Phase

InnerLife's tick consumes the event buffer (snapshot + clear at lines
104-111 of `doTick()`) and evaluates with the channel context passed to
`assemblePrompt()`. If CognitionCore ticked InnerLife with
`channelContext=null`, the LLM evaluates without channel awareness, AND
the buffer is consumed — a later `evaluateProactive(channelContext)`
finds an empty buffer and returns Silent. Channel-aware motivation
assessment is lost.

InnerLife needs channel context ("should I speak in Slack?" differs
from "should I speak in email?") which is not available during a
scheduled tick cycle. It remains on-demand.

#### Changes to InnerLifeOrchestrator

1. **Remove `driveOrchestrator.tick()` call** from `doTick()` line 97.
   Drives are guaranteed current by CognitionCore's phase ordering.

2. **Add precondition check** — at the start of `doTick()`, assert
   that drives have a current profile:
   ```java
   if (driveOrchestrator.currentDrives(
           descriptor.agentId(), descriptor.tenancyId()).isEmpty()) {
       LOG.log(Level.WARNING,
           "InnerLife tick called without current drive profile for "
           + descriptor.agentId() + " — ensure CognitionCore.tick() "
           + "has run first");
       return new InnerLifeTick.Silent("drives not current");
   }
   ```
   This is a degraded-mode fallback (log + Silent), not a hard failure.
   It enforces the ordering contract at runtime — a consumer calling
   `evaluateProactive()` without a prior CognitionCore tick gets a
   warning and a no-op rather than stale data.

3. **Keep `DriveOrchestrator` constructor parameter** — needed for
   the precondition check via `currentDrives()`. The parameter stays
   but is read-only (no more `tick()` calls).

#### Changes to SocialAvatarCognition

`evaluateProactive()` continues to call `ProactiveSpeechSupport` →
`innerLife.tick(descriptor, channelContext)`. The invocation path is
unchanged — InnerLife evaluates on-demand with channel context.
The only difference: InnerLife no longer re-ticks drives internally,
because CognitionCore has already done so.

`SocialAvatarCognition` passes InnerLifeOrchestrator to CognitionCore's
constructor and removes its separate `innerLife` field. Access to
InnerLife for `evaluateProactive()` goes through
`core.innerLife()`.

## Ordering Fix (D6)

The drive tick moves from position 4 (before strategy, userModel,
mentalModel) to DERIVED phase (after SOURCE and SOURCE_PER_SUBJECT).
This fixes the data staleness for:

- `CompetenceDrive` → reads `StrategyLearningOrchestrator.engagementTrend()`
- `AffiliationDrive` → reads `UserModelOrchestrator.activeProfiles()`
- `AutonomyDrive` → reads `MentalModelOrchestrator.activeSnapshots()`

All three now see current-tick data instead of one-tick-stale data.
`CuriosityDrive` → `MemoryHygieneOrchestrator.knowledgeGaps()` is
unaffected (memoryHygiene is FOUNDATION, already ran first).

This aligns with the #136 spec ordering constraint: "source
orchestrators → DriveOrchestrator → GoalProposalOrchestrator."

## Scope Boundary

**In scope:**
- `CognitionPhase` enum in `blocks-core`
- `CognitionTickParticipant` and `CognitionTickContext` in `blocks-core`
- Refactored `CognitionCore.tick()` with phase-based dispatch
- `addParticipant()` registration on CognitionCore
- InnerLifeOrchestrator as CognitionCore field (not phase participant)
- InnerLife drive re-tick removal + precondition check
- `innerLifeEnabled` flag in CognitionConfig
- Drive ordering fix
- `SocialAvatarCognition` reorganisation (InnerLife through CognitionCore)

**Out of scope:**
- Timer-based scheduling (consumer concern — they use
  `ScheduledExecutorService` or Quarkus `@Scheduled`)
- Event-based tick triggering (consumer concern)
- YAML configuration of phases (future #246 scope)
- Per-subject custom participants
- InnerLife goal-level initiative (#136 child 4 — separate follow-up)

## Testing Strategy

- **CognitionCoreTest**: verify phase ordering — mock orchestrators,
  assert tick call order via `InOrder`
- **CognitionCoreTest**: verify custom participant execution at each
  phase, including error isolation
- **CognitionCoreTest**: verify `SOURCE_PER_SUBJECT` rejects custom
  participants
- **CognitionCoreTest**: verify `CognitionConfig` disable flags skip
  built-in orchestrators but not custom participants
- **CognitionCoreTest**: verify `innerLifeEnabled=false` prevents
  InnerLife evaluation
- **InnerLifeOrchestratorTest**: verify `driveOrchestrator.tick()` is
  no longer called from `doTick()`
- **InnerLifeOrchestratorTest**: verify precondition check returns
  Silent with warning when drives not current
- **InnerLifeOrchestratorTest**: verify channel-aware evaluation still
  works via direct `tick()` call with channelContext

## References

- CognitionCore.java — current tick ordering (lines 97-137), constructor overloads
- InnerLifeOrchestrator.java — redundant drive tick (line 97), buffer consumption (lines 104-111), content quality gate (lines 130-143), assemblePrompt channelContext (line 191)
- DriveOrchestrator.java — drive source dependencies (CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive)
- SocialAvatarCognition.java — evaluateProactive() invocation pattern, CognitionCore construction
- ProactiveSpeechSupport.java — InnerLife tick delegation (line 21)
- CognitionConfig.java — per-subsystem enable/disable flags
- PipelineTickScheduler.java — existing tick pattern in summarisation
- #136 spec — ordering constraint: "source orchestrators → DriveOrchestrator → GoalProposalOrchestrator"
- ADR-0001 — compositor pattern (tick without record)
- Decision review R1-01/R1-02/R1-03 — InnerLife integration analysis
- Spec review R1-02 — channel-aware evaluation regression
- Spec review R1-06 — SubjectResolver in context
- Spec review R1-07 — innerLifeEnabled consistency
