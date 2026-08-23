# Autonomous Goal Generation — Design Spec

**Issue:** casehubio/blocks#136
**Date:** 2026-08-23
**Branch:** issue-136-autonomous-goal-generation
**Repos:** blocks (primary), engine-api, engine runtime, eidos-api

## Overview

Layer 2 of the autonomous intelligence research direction. Translates intrinsic
drive signals (from Drive Architecture, #129) into concrete `AgentGoal` instances
that the engine's existing goal lifecycle can decompose and execute.

> **Terminology note:** Issue #136's body references `GoapGoal`, which predates
> the current type system. This spec uses `AgentGoal` (eidos-api) throughout.

The engine already has a complete goal lifecycle — formation, decomposition,
revision, abandonment, routing signals, and completion marking. The gap is that
goal formation is case-bound: `GoalFormationEvaluator.evaluate(workerName,
caseInstance, insights)` requires a running case. Autonomous goals fire during
idle time when no case is active.

This spec bridges that gap with three changes:

1. **Engine refactoring** — extract case-independent `GoalFormationService` SPI
   from `GoalFormationEvaluator`
2. **eidos-api extension** — add `@Nullable Map<String, String> attributes` to
   `AgentGoal` for goal provenance
3. **Blocks orchestrator** — `GoalProposalOrchestrator` (compositor pattern) that
   reads drives, maps to goal proposals, and caches them for registration

## Architecture

```
  Scheduler (consumer — quarkmind, claudony, etc.)
  │
  │  tick ordering: source orchestrators → DriveOrchestrator
  │                 → GoalProposalOrchestrator → register proposals
  │
  ├─── 1. tick source orchestrators (MemoryHygiene, Strategy, UserModel, MentalModel)
  ├─── 2. DriveOrchestrator.tick() → DriveProfile (cached)
  ├─── 3. GoalProposalOrchestrator.tick() → GoalProposalTick (cached)
  │         │
  │         ├── reads DriveProfile from DriveOrchestrator.currentDrives()
  │         ├── evaluates DriveGoalMapper per axis (heuristic)
  │         ├── checks capacity (maxDriveGoals, deduplication)
  │         ├── re-evaluates relevance of existing drive-sourced goals
  │         └── caches proposals — NO side effects (compositor guarantee)
  │
  └─── 4. GoalFormationService.propose(agentId, tenantId, proposal)
            │
            ├── validates (name length, capacity, deduplication)
            ├── merges goals onto AgentDescriptor via AgentRegistry
            ├── writes audit log (EventLog, GOAL_FORMED)
            └── returns GoalFormationResult

  Engine downstream (unchanged — operates on registered goals):
  ├── DefaultGoalDecomposer → DagPlan via DecompositionStrategy
  ├── GoapPlanningStrategy → selects next binding to fire
  ├── GoalRevisionEvaluator → revises from worker outcomes
  ├── GoalAbandonmentEvaluator → failure-count threshold
  ├── GoalSignalProvider → routing signal from goal ratio
  └── AgentGoalCompletionMarker → marks goals met in context
```

### Compositor Pattern (ADR-0001)

`GoalProposalOrchestrator` follows the compositor pattern established by
`DriveOrchestrator` (#129): `tick()` evaluates and caches, `currentProposals()`
returns cached state, no side effects. The scheduler is responsible for the
effectful step (calling `GoalFormationService.propose()`). This enables:

- Inspection of proposals before registration
- Replay without duplicate registrations
- Alignment with the engine's `autoApprove` governance concept
- No ordering sensitivity with other consumers

### Ordering Constraint

Source orchestrators → `DriveOrchestrator` → `GoalProposalOrchestrator`. The
scheduler owns this sequence, same as the #129 constraint that drive ticks follow
source orchestrator ticks.

## Type System

### Engine-api (new)

#### GoalFormationService (SPI)

```java
public interface GoalFormationService {
    GoalFormationResult propose(String agentId, String tenancyId,
                                GoalFormationProposal proposal);
}
```

Single method. Accepts pre-built proposals (from any source — reflection-driven,
drive-driven, future sources). Handles validation, deduplication, capacity check,
`AgentRegistry` registration, audit logging. The implementation lives in engine
runtime.

#### GoalFormationResult (new record)

```java
public record GoalFormationResult(
        List<AgentGoal> registered,
        List<RejectedGoal> rejected,
        int totalGoalCount) {

    public record RejectedGoal(String name, String reason) {}
}
```

#### GoalFormationProposal.ProposedGoal (extension)

```java
public static record ProposedGoal(
        String name,
        String description,
        GoalPriority suggestedPriority,
        String formationReason,
        @Nullable Map<String, String> attributes) {  // NEW — nullable for sources that don't need provenance
    public ProposedGoal {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(formationReason, "formationReason must not be null");
        attributes = attributes != null ? Map.copyOf(attributes) : null;
    }
}
```

Adds `@Nullable Map<String, String> attributes` to `ProposedGoal`. This completes
the attribute propagation path: `DriveGoalProposal` → scheduler converts to
`ProposedGoal` with `attributes = {"source": "drive", "driveAxis": axis.name()}`
→ `GoalFormationService` passes attributes through to `AgentGoal`. Without this,
D7 capacity counting and D8 relevance re-evaluation cannot function. Existing
reflection-sourced callers pass `null`.

### eidos-api (extension)

#### AgentGoal — add attributes field

```java
public record AgentGoal(
        String name,
        String description,
        GoalPriority priority,
        Visibility visibility,
        List<String> capabilities,
        @Nullable Map<String, String> attributes) {  // NEW — nullable for backward compat
    // compact constructor: attributes = attributes != null ? Map.copyOf(attributes) : null
}
```

Drive-sourced goals store `{"source": "drive", "driveAxis": "CURIOSITY"}`.
Reflection-sourced goals store `{"source": "reflection"}` or null.

Follows the platform `Memory.attributes` pattern. Builder gains an
`attributes(Map<String, String>)` method. Adding the sixth record component is
source-breaking: all call sites using the 5-arg canonical constructor must be
migrated to pass `null` as the sixth argument. Known call sites:
`GoalFormationEvaluator.validateAndConvert()`, `CognitiveObservationSectionsTest`,
and application-tier code constructing `AgentGoal` directly. This is intentional —
the breakage forces every caller to be explicit about attributes.

### Blocks (new package: `io.casehub.blocks.agentic.social.goal`)

#### DriveGoalMapper (functional interface — SPI)

```java
@FunctionalInterface
public interface DriveGoalMapper {
    @Nullable DriveGoalProposal evaluate(String agentId, String tenantId,
                                         DriveIntensity intensity);
}
```

Returns null when no goal is warranted (intensity too low, no specific target
found). Each implementation injects its source orchestrator for structured context.

#### DriveGoalProposal (record)

```java
public record DriveGoalProposal(
        DriveAxis axis,
        String goalName,
        String goalDescription,
        String formationReason,
        double driveIntensity) {
    public DriveGoalProposal {
        Objects.requireNonNull(axis);
        Objects.requireNonNull(goalName);
        Objects.requireNonNull(goalDescription);
        Objects.requireNonNull(formationReason);
    }
}
```

Blocks-internal type. Converted to engine-api `GoalFormationProposal` at the
registration boundary.

#### GoalProposalTick (sealed interface)

```java
public sealed interface GoalProposalTick {
    record NoChange(@Nullable String reason) implements GoalProposalTick {}
    record Proposed(List<DriveGoalProposal> newProposals,
                    List<String> abandonedGoalNames) implements GoalProposalTick {}
}
```

`NoChange` when no proposals warranted (all drives below threshold, capacity
full, cooldown active). `Proposed` carries new proposals AND goals to abandon
(from relevance re-evaluation).

#### GoalProposalConfig (record)

```java
public record GoalProposalConfig(
        double proposalThreshold,
        double relevanceThreshold,
        int maxDriveGoals,
        Duration staleAfter,
        Duration cooldown,
        double escalationThreshold,
        int failureAbandonmentThreshold) {

    public static GoalProposalConfig defaults() {
        return new GoalProposalConfig(
            0.4,                    // propose when drive intensity > 0.4
            0.2,                    // abandon when supporting drive < 0.2
            3,                      // max 3 of MAX_GOALS=10 for drive goals
            Duration.ofMinutes(120), // stale after 2 hours below relevance
            Duration.ofMinutes(60),  // cooldown between proposal cycles
            1.1,                    // escalation disabled (> 1.0 = never)
            5                       // failure count to treat as abandoned
        );
    }
}
```

#### GoalProposalOrchestrator

```java
@ApplicationScoped
public class GoalProposalOrchestrator {

    private final DriveOrchestrator driveOrchestrator;
    private final List<DriveGoalMapper> mappers;
    private final Instance<GoalSignalStore> goalSignalStore;
    private final GoalProposalConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, GoalProposalState> states =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks =
        new ConcurrentHashMap<>();

    // CDI constructor: injects Instance<DriveGoalMapper> for discovery
    // Package-private test constructor with Clock

    public GoalProposalTick tick(String agentId, String tenantId,
                                 AgentDescriptor descriptor) { ... }

    public Optional<List<DriveGoalProposal>> currentProposals(
            String agentId, String tenantId) { ... }
}
```

**tick() flow:**

1. Read `DriveProfile` from `driveOrchestrator.currentDrives(agentId, tenantId)`
2. If empty or cooldown active → return `NoChange`
3. Count existing drive-sourced goals on descriptor (filter by
   `attributes.get("source").equals("drive")`)
4. Remaining capacity = `config.maxDriveGoals() - existingDriveGoalCount`
5. Call each `DriveGoalMapper.evaluate()` for axes above `proposalThreshold` —
   the orchestrator owns threshold enforcement; mappers focus on content evaluation
   (finding gaps, subjects, trends) and return null when no actionable target exists
6. Collect non-null proposals, **filter out names in the failure suppression set**
   (see step 7b), rank by drive intensity, take top N = remaining capacity
7. **Relevance re-evaluation**: for each existing drive-sourced goal:
   - **a. Drive-stale check**: if the originating axis intensity is below
     `relevanceThreshold` for longer than `staleAfter`, add to abandonment list
   - **b. Failure-abandoned check**: if `GoalSignalStore.outcomeCounts()` shows
     `failureCount >= failureAbandonmentThreshold` for the goal, add to
     abandonment list AND add the goal name to the **failure suppression set**
     on `GoalProposalState` (reclaims capacity for goals the engine has
     effectively abandoned via `GoalAbandonmentEvaluator` filtering but not
     removed from the descriptor)
8. Cache proposals + abandonment list as `GoalProposalTick.Proposed`
9. Return the tick result (NO registration — compositor guarantee)

**Failure suppression**: `GoalProposalState` maintains a `Set<String>` of goal
names that were failure-abandoned. Step 6 filters these names from new proposals,
preventing a register→abandon cycle where persistent `GoalSignalStore` failure
counts cause immediate re-abandonment of re-proposed goals with the same name.
The suppression set is in-memory only — clears on restart (one wasted cycle,
acceptable). When #162 provides topic-specific goal names
("explore-physics" vs "explore-biology"), suppression is per-name: failing on
one topic does not block other topics on the same axis.

**The scheduler** reads `currentProposals()` or the tick result and:
- Converts `DriveGoalProposal` to `GoalFormationProposal.ProposedGoal` with
  SECONDARY priority and `attributes = {"source": "drive", "driveAxis": axis.name()}`
- Calls `GoalFormationService.propose()` to register
- For abandoned goals, calls `GoalRemovalService.removeGoals()` to remove them

### Consumer Integration Example

The scheduler is consumer-specific (quarkmind, claudony, etc.) — no SPI is
prescribed. The following shows the full tick→evaluate→register flow:

```java
// Consumer wires the tick loop — same pattern as InnerLife today
void autonomousTick(String agentId, String tenancyId, AgentDescriptor descriptor) {
    // 1-2. Source orchestrators + DriveOrchestrator already ticked

    // 3. Goal proposal evaluation (compositor — no side effects)
    var tick = goalProposalOrchestrator.tick(agentId, tenancyId, descriptor);

    // 4. Effectful registration (scheduler responsibility)
    if (tick instanceof GoalProposalTick.Proposed proposed) {
        if (!proposed.newProposals().isEmpty()) {
            var goals = proposed.newProposals().stream()
                .map(p -> new GoalFormationProposal.ProposedGoal(
                    p.goalName(), p.goalDescription(),
                    GoalPriority.SECONDARY, p.formationReason(),
                    Map.of("source", "drive", "driveAxis", p.axis().name())))
                .toList();
            goalFormationService.propose(agentId, tenancyId,
                new GoalFormationProposal(goals, "Drive-sourced autonomous goals"));
        }
        if (!proposed.abandonedGoalNames().isEmpty()) {
            goalRemovalService.removeGoals(agentId, tenancyId,
                proposed.abandonedGoalNames(), "drive relevance below threshold");
        }
    }
}
```

The conversion from `DriveGoalProposal` to `GoalFormationProposal.ProposedGoal`
happens here — in the scheduler — not in the orchestrator. This preserves the
compositor guarantee (no side effects in tick) and keeps blocks types
(`DriveGoalProposal`) separate from engine-api types (`ProposedGoal`).

## Drive Goal Mapper Implementations

### CuriosityGoalMapper

**Source:** `MemoryHygieneOrchestrator.knowledgeGaps(agentId, tenantId)`
**Signal:** Knowledge gaps — fragmented or poorly understood knowledge areas

```java
public class CuriosityGoalMapper implements DriveGoalMapper {
    private final MemoryHygieneOrchestrator hygieneOrchestrator;

    CuriosityGoalMapper(MemoryHygieneOrchestrator hygieneOrchestrator) { ... }

    @Override
    public @Nullable DriveGoalProposal evaluate(String agentId, String tenantId,
                                                 DriveIntensity intensity) {
        var gaps = hygieneOrchestrator.knowledgeGaps(agentId, tenantId);
        if (gaps == null || gaps.lowRetentionCount() == 0) return null;
        return new DriveGoalProposal(
            DriveAxis.CURIOSITY,
            "explore-knowledge-gaps",
            "Explore fragmented knowledge areas ("
                + gaps.lowRetentionCount() + " low-retention memories across "
                + gaps.consolidationGroups() + " groups)",
            "curiosity: " + gaps.lowRetentionCount() + " low-retention memories"
                + " across " + gaps.consolidationGroups() + " groups",
            intensity.intensity());
    }
}
```

### CompetenceGoalMapper

**Source:** `StrategyLearningOrchestrator.engagementTrend(agentId, tenantId)`
**Signal:** Declining engagement dimensions

```java
public class CompetenceGoalMapper implements DriveGoalMapper {
    private final StrategyLearningOrchestrator strategyOrchestrator;

    // evaluate(): find the most declining dimension, propose
    // "improve-{dimension}" goal
}
```

### AffiliationGoalMapper

**Source:** `UserModelOrchestrator.activeProfiles(agentId, tenantId)`
**Signal:** Neglected relationships (low familiarity, stale interaction)

```java
public class AffiliationGoalMapper implements DriveGoalMapper {
    private final UserModelOrchestrator userModelOrchestrator;
    private final double decayThreshold;
    private final Duration staleDuration;

    // evaluate(): find the most neglected subject, propose
    // "reconnect-{subjectId}" goal
}
```

### AutonomyGoalMapper

**Source:** `MentalModelOrchestrator.activeSnapshots(agentId, tenantId)`
**Signal:** High-confidence intention projections (value misalignment)

```java
public class AutonomyGoalMapper implements DriveGoalMapper {
    private final MentalModelOrchestrator mentalModelOrchestrator;
    private final double confidenceFloor;

    // evaluate(): count high-confidence intention projections, propose
    // "reassess-{subject}" goal
}
```

## Engine Refactoring

### GoalFormationService extraction

Extract from `GoalFormationEvaluator.formGoals()` (lines 163-225):

1. Validation logic (lines 227-265): name length, description length,
   deduplication, capacity check (`MAX_GOALS`) → service method
2. Registration logic (lines 196-199): merge goals onto descriptor,
   `agentRegistry.register()` → service method
3. Audit logging (lines 292-331): EventLog entry (`GOAL_FORMED`) → service method

`GoalFormationService` handles only universal validations — name length,
description length, deduplication, total capacity (`MAX_GOALS`). Per-source rate
limits stay in callers:
- `GoalFormationEvaluator`: pre-trims proposals to
  `min(maxNewPerReflection, remaining)` before calling the service
- `GoalProposalOrchestrator`: limits proposals to
  `maxDriveGoals - existingDriveGoals` in tick()

`GoalFormationEvaluator` becomes a thin trigger that:
1. Extracts agentId/tenantId from CaseInstance + CaseDefinition
2. Retrieves memories
3. Calls `GoalFormationStrategy.propose()` to get proposals
4. Pre-trims to `maxNewPerReflection`
5. If `autoApprove=true`: delegates to `GoalFormationService.propose()` for
   registration (the service always registers — it has no propose-without-register
   mode)
6. If `autoApprove=false`: writes `GOAL_PROPOSED` audit event directly, does NOT
   call GoalFormationService (goals are proposed for review but not registered)

For drive-sourced goals (via the scheduler), `autoApprove` does not apply — the
scheduler always calls GoalFormationService. Drive-sourced goals have their own
governance: SECONDARY priority ensures they only execute during idle time.

The cooldown mechanism stays in `GoalFormationEvaluator` (reflection-driven
cooldown) and `GoalProposalOrchestrator` (drive-driven cooldown) — each trigger
manages its own rate limiting.

### GoalRemovalService extraction

Extract the goal removal + registration + audit subset from
`GoalRevisionEvaluator.handleEvolved()` into a reusable service. The case-bound
evaluator's ABANDON/COMPLETE actions and blocks' relevance re-evaluation both need
to remove named goals from a descriptor.

#### GoalRemovalService (SPI)

```java
public interface GoalRemovalService {
    GoalRemovalResult removeGoals(String agentId, String tenancyId,
                                  List<String> goalNames, String reason);
}

public record GoalRemovalResult(
        List<String> removedGoals,
        int remainingGoalCount) {}
```

The implementation:
1. Loads descriptor from `AgentRegistry`
2. Filters out named goals from `descriptor.goals()`
3. Registers updated descriptor via `AgentRegistry`
4. Writes audit log (`EventLog`, `GOAL_REMOVED`)
5. Returns result with actually removed names and remaining count

The full revision flow (evolution evaluation, strategy-based REVISE/COMPLETE,
signal clearing) stays in `GoalRevisionEvaluator`. This extraction is minimal —
only the shared "remove + register + audit" step.

## Capacity Allocation (D7)

Drive-sourced goals occupy a separate budget within `MAX_GOALS=10`:

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `maxDriveGoals` | 3 | Maximum drive-sourced goal slots |
| `driveFormationCooldown` | 60 min | Minimum interval between proposal cycles |

When 4 axes produce proposals but only N slots are available, selection is by
highest modulated drive intensity. Ties broken by `DriveAxis` ordinal
(CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY).

## Goal Lifecycle (D8)

Drive-sourced goals have a relevance re-evaluation mechanism:

1. On each `GoalProposalOrchestrator.tick()`, existing drive-sourced goals are
   checked against current drive state
2. If the originating axis intensity drops below `relevanceThreshold` (0.2)
   for longer than `staleAfter` (120 min), the goal is marked for removal
3. The scheduler removes stale goals via `AgentRegistry`

This supplements the engine's `GoalAbandonmentEvaluator` (failure-count-based)
which cannot detect stale goals that were never attempted.

### Restart Semantics

`GoalProposalOrchestrator` stores all state in-memory
(`ConcurrentHashMap<String, GoalProposalState>`). On restart, three timers reset:

1. **Cooldown reset**: The first tick after restart may produce proposals
   regardless of how recently goals were formed. Mitigated by
   `GoalFormationService` deduplication — re-proposing already-registered goals
   is rejected by name-match validation.
2. **Relevance timer reset**: A goal 119 minutes into its 120-minute stale
   evaluation gets a fresh 120 minutes. This is a bounded delay (max
   `staleAfter` duration), not a correctness issue.
3. **Proposal state lost**: The orchestrator may re-propose goals that are
   already registered. Mitigated by `GoalFormationService` deduplication and
   capacity validation at registration time.

This follows the same in-memory pattern as `DriveOrchestrator` (ADR-0001: "No
persistence needed — recomputable on restart"). The difference:
`DriveOrchestrator` produces derived state with no side effects;
`GoalProposalOrchestrator` produces proposals that lead to registration side
effects via the scheduler. The registration-time validations
(deduplication, capacity) are the correctness backstop — restart may cause
redundant evaluation but not incorrect state.

## Priority Model (D6)

- Drive-sourced goals always use `GoalPriority.SECONDARY`
- No PRIMARY escalation in Layer 2 (ungoverned — see D6 rationale)
- The engine's existing priority system handles sequencing: SECONDARY goals
  execute only when no PRIMARY work is available
- The priority system IS the idle-time mechanism

### Visibility

Drive-sourced goals use `Visibility.PUBLIC`. They are legitimate agent objectives
that must be visible in the agent's observation prompt
(`CognitiveObservationSections.goalsSection()` renders all goals sorted by
priority) and accessible to the engine's planning system
(`DefaultGoalDecomposer`, `GoapPlanningStrategy`). PRIVATE would hide them from
the agent's own cognitive loop, preventing execution. Drive-sourced goals are
not "hidden internal motivations" — drives are the internal signals (rendered
separately in the Motivational State section); goals are the concrete actions
the agent commits to pursuing.

## Package Structure

```
blocks/src/main/java/io/casehub/blocks/agentic/social/goal/
├── DriveGoalMapper.java          (@FunctionalInterface SPI)
├── DriveGoalProposal.java        (blocks-internal record)
├── GoalProposalTick.java         (sealed: NoChange, Proposed)
├── GoalProposalConfig.java       (config record with defaults())
├── GoalProposalOrchestrator.java (compositor lifecycle)
├── CuriosityGoalMapper.java
├── CompetenceGoalMapper.java
├── AffiliationGoalMapper.java
└── AutonomyGoalMapper.java

engine/api/src/main/java/io/casehub/api/spi/routing/
├── GoalFormationService.java     (NEW SPI)
├── GoalFormationResult.java      (NEW record)
├── GoalRemovalService.java       (NEW SPI)
├── GoalRemovalResult.java        (NEW record)
└── GoalFormationProposal.java    (EXTENDED — add attributes to ProposedGoal)

engine/runtime/src/main/java/io/casehub/engine/internal/routing/
├── DefaultGoalFormationService.java  (NEW implementation)
├── DefaultGoalRemovalService.java    (NEW implementation)
├── GoalFormationEvaluator.java       (REFACTORED — delegates to service)
└── GoalRevisionEvaluator.java        (REFACTORED — delegates to removal service)

eidos/api/src/main/java/io/casehub/eidos/api/
└── AgentGoal.java                    (EXTENDED — add @Nullable attributes)

eidos/runtime/src/main/java/io/casehub/eidos/runtime/registry/jpa/
├── AgentGoalEntity.java              (EXTENDED — add attributes TEXT column)
├── AgentDescriptorMapper.java        (UPDATED — map attributes to/from JSON)
└── V*__add_goal_attributes.sql       (NEW Flyway migration)
```

## Testing Strategy

### Per-mapper unit tests

Each `DriveGoalMapper` tested independently with mocked source orchestrator.
Verify: correct goal name/description from source data, null return when
intensity below threshold, null return when no specific target found, edge
cases (empty profiles, zero gaps, no subjects).

### GoalProposalOrchestrator tests

Mocked `DriveOrchestrator` and `DriveGoalMapper` instances. Verify:

- tick lifecycle: NoChange when drives weak, Proposed when drives strong
- Capacity enforcement: maxDriveGoals respected
- TOCTOU safety: capacity check at tick time is advisory; registration-time
  deduplication in GoalFormationService is the correctness backstop
- Selection: highest intensity wins when proposals exceed capacity
- Relevance re-evaluation: goals abandoned when supporting drive weakens
- Failure-abandoned detection: goals abandoned when GoalSignalStore failure count
  exceeds threshold; graceful handling when GoalSignalStore is not resolvable
- Failure suppression: re-proposal of failure-abandoned goal names suppressed;
  register→abandon cycle does not occur
- Cooldown: proposals suppressed within cooldown window
- Compositor guarantee: tick() produces same result on re-tick without
  registration (no side effects)
- State key management: per-agent isolation

### GoalFormationService tests

Mocked `AgentRegistry`, `EventLogRepository`. Verify:

- Validation: name length, description length, duplicate rejection
- Capacity: MAX_GOALS respected, remaining capacity computed correctly
- Registration: goals merged onto descriptor, `register()` called
- Audit: EventLog written with correct event type and metadata
- Result: registered and rejected lists populated correctly
- Attributes propagation: drive provenance attributes preserved

### GoalFormationEvaluator refactoring tests

Existing tests continue to pass — behavior unchanged, only internal delegation
changed to call GoalFormationService.

### Integration test

Full tick cycle: set up source orchestrator state → tick DriveOrchestrator →
tick GoalProposalOrchestrator → verify proposals → simulate registration via
GoalFormationService → verify goals on AgentDescriptor.

All tests are plain JUnit 5 with Mockito (no Quarkus runtime), consistent with
the blocks testing pattern.

## Upstream Changes Required

| Repo | Change | Why |
|------|--------|-----|
| engine-api | `GoalFormationService` SPI + `GoalFormationResult` record | Case-independent goal registration |
| engine-api | Add `GOAL_REMOVED` to `CaseHubEventType` enum | Audit event for goal removal via GoalRemovalService |
| engine-api | Extend `GoalFormationProposal.ProposedGoal` with `@Nullable Map<String, String> attributes` | Attribute propagation from proposal to registered goal |
| engine runtime | `DefaultGoalFormationService` impl, refactor `GoalFormationEvaluator` | Extract reusable validation/registration/audit |
| engine runtime | `DefaultGoalRemovalService` impl, refactor `GoalRevisionEvaluator` | Reusable goal removal for drive lifecycle |
| eidos-api | Add `@Nullable Map<String, String> attributes` to `AgentGoal` | Goal provenance for capacity + lifecycle tracking |
| eidos runtime | Add `attributes TEXT` column to `AgentGoalEntity` | Persist goal provenance |
| eidos runtime | Update `AgentDescriptorMapper.toGoal()` / `toGoalEntity()` | Map `attributes` field to/from JSON column |
| eidos runtime | Flyway migration: add `attributes` column to `agent_goal` table | Schema evolution |

Engine changes are new SPI + impl or internal refactoring (evaluator delegates to
service). The eidos-api change adds a sixth record component — source-breaking
(see §Type System). The eidos runtime persistence changes add a nullable TEXT
column storing attributes as JSON, following the same pattern as
`AgentGoalEntity.capabilities`.

## What This Does NOT Include

- **Scheduling** — consumers wire up the tick loop. Same as InnerLife today.
  See Consumer Integration Example above for the full flow.
- **InnerLife changes** — InnerLife stays focused on speech initiation.
  GoalProposalOrchestrator IS the goal-level initiative mechanism (issue #136
  child 4 "IdleTimeGoalActivation"). It lives as a separate orchestrator rather
  than inside InnerLife because goal proposal is heuristic (drive-to-goal mapping)
  while InnerLife uses LLM for motivation scoring — different evaluation mechanisms
  in different orchestrators. The issue's design principle "InnerLife evolves from
  'should I speak?' to 'should I act?'" is addressed via composition: InnerLife
  handles speech, GoalProposalOrchestrator handles action, a shared scheduler
  coordinates both.
- **GoalPriorityAdjudicator** — issue #136 child 3. The priority hierarchy
  (SECONDARY for drive-sourced, PRIMARY for case-assigned) IS the adjudication
  mechanism for Layer 2. Dynamic priority based on drive intensity requires
  governance (Layer 3 narrative identity). Filed as follow-up.
- **Cross-axis goal composition** — compound goals (curiosity + affiliation)
  deferred to Layer 3 narrative identity.
- **PRIMARY priority escalation** — requires governance (Layer 3).
- **LLM-first goal formation** — heuristic default; LLM refinement is opt-in.
- **GoalFormationContext extension** — no drive fields on engine-api types.
- **Quarkmind integration** — quarkmind wires the tick loop when ready.

## Follow-up Issues

| # | Title | Scope |
|---|-------|-------|
| #158 | DriveGoalFormationStrategy — purpose-built LLM prompt for drive-to-goal | Replaces reused reflection prompt with drive-specific framing |
| #159 | Cross-axis goal composition (Layer 3) | "learn about X by engaging with Y" (curiosity + affiliation) |
| #160 | PRIMARY escalation governance (Layer 3) | Narrative identity provides justification for priority elevation; addresses #136 child 3 (GoalPriorityAdjudicator) |
| #161 | InnerLife scheduler SPI | Shared scheduling for InnerLife + GoalProposal tick loops; addresses #136 child 4 (IdleTimeGoalActivation) |
| #162 | DriveSource intermediate caching | CuriosityDrive.lastGaps() for mapper consumption without redundant reads |
| #163 | Quarkmind integration | Wire GoalProposalOrchestrator into quarkmind's tick cycle |

## References

- `GoalFormationEvaluator.java` (engine runtime) — existing case-bound formation
- `GoalFormationStrategy.java` (engine-api) — existing formation SPI
- `GoalFormationContext.java` (engine-api) — existing context (case-independent)
- `GoalFormationProposal.java` (engine-api) — existing proposal type
- `GoalRevisionEvaluator.java` (engine runtime) — existing case-bound revision
- `GoalRevisionStrategy.java` (engine-api) — existing revision SPI
- `DefaultGoalDecomposer.java` (engine planning) — goal→DagPlan decomposition
- `GoalAbandonmentEvaluator.java` (engine runtime) — failure-count abandonment
- `GoalSignalProvider.java` (engine routing) — goal ratio routing signal
- `AgentGoalCompletionMarker.java` (engine runtime) — goal-met context markers
- `AgentGoal.java` (eidos-api) — current 5-field record
- `GoalPriority.java` (eidos-api) — PRIMARY/SECONDARY enum
- `DriveOrchestrator.java` (blocks #129) — compositor pattern reference
- `DriveProfile.java` (blocks #129) — per-axis intensities
- `DriveSource.java` (blocks #129) — functional interface pattern
- ADR-0001 — compositor pattern (tick without record)
- `Memory.java` (neocortex-memory-api) — `Map<String, String> attributes` pattern
- Research blog `2026-08-21-mdp04-what-does-the-agent-want.md` — three-layer vision
- [GE-20260811-e941cc] — AgentDisposition vs DispositionProfile type split
- `HeuristicMessageSummariser.java` (blocks) — heuristic/LLM tiering pattern
- decisions.md — 9 validated decisions (D1-D9)
