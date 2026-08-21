# Drive Architecture — Design Spec

**Issue:** casehubio/blocks#129
**Date:** 2026-08-21
**Branch:** issue-129-drive-architecture
**Repos:** blocks (primary), eidos, neocortex

## Overview

Drive Architecture adds an intrinsic motivation system to the social cognition
stack. A `DriveOrchestrator` reads four source orchestrators' outputs and
synthesises motivational signals along four axes: curiosity (from intrinsic
motivation literature — Oudeyer & Kaplan 2007, Berlyne 1954), competence,
affiliation, and autonomy (the latter three from Self-Determination Theory —
Deci & Ryan 2000). Drives emerge from data the agent has already
accumulated — an agent that has never experienced a knowledge gap has no
curiosity drive.

This is Layer 1 of three (see research blog 2026-08-21-mdp04). Layer 2
(autonomous goal generation, #136) and Layer 3 (narrative identity, #142)
build on this foundation but are out of scope.

## Architecture

```
          DriveOrchestrator                    DriveComposer
          ┌────────────────────────────┐       ┌──────────────────────┐
          │  tick(agentId, tenantId,   │       │  compose(raw drives, │
          │       AgentDescriptor)     │       │    AgentDisposition, │
          │                            │       │    MoodState,        │
          │  1. evaluate DriveSource[] │──────▶│    DriveConfig)      │
          │  2. delegate to composer   │       │                      │
          │  3. cache DriveProfile     │       │  → modulate          │
          │  4. return DriveTick       │       │  → weight + compose  │
          │                            │       │  → return DriveProfile│
          │  currentDrives() → cached  │       └──────────────────────┘
          └──────┬───────────────┬─────┘
                 │               │
            evaluation       evaluation
                 │               │
          ┌──────┴─────┐  ┌─────┴──────────┐
          │ Curiosity  │  │ Competence     │
          │ Drive      │  │ Drive          │
          └──────┬─────┘  └──────┬─────────┘
                           │                │
          ┌──────┴────┐    ┌──────┴──────────┐
          │ Memory    │    │ StrategyLearning │
          │ Hygiene   │    │ Orchestrator     │
          └───────────┘    └─────────────────┘

          ┌─────────────┐   ┌──────────────────┐
          │ Affiliation │   │ Autonomy         │
          │ Drive       │   │ Drive            │
          └──────┬──────┘   └────────┬─────────┘
                 │                   │
          ┌──────┴──────┐   ┌────────┴─────────┐
          │ UserModel   │   │ MentalModel      │
          │ Orchestr.   │   │ Orchestrator     │
          └─────────────┘   └──────────────────┘

  Modulation inputs (passed to DriveComposer):
    AgentDescriptor.disposition() ─── personality traits (tick parameter)
    MoodOrchestrator.currentMood() ── PAD emotional state (constructor dep)
```

### Data Flow

1. Source orchestrators accumulate signals via their normal `record()` + `tick()`
   lifecycle (unchanged).
2. `DriveOrchestrator.tick(agentId, tenantId, descriptor)` calls each
   `DriveSource.evaluate()` which reads the source orchestrator's current state
   via public accessors. Returns raw `DriveIntensity` per axis.
3. `DriveComposer.compose()` takes the raw intensities, `AgentDisposition`
   (from the descriptor tick parameter), and `MoodState` (from MoodOrchestrator).
   It applies personality and mood modulation, then weighted composition.
4. The resulting `DriveProfile` is cached; `currentDrives()` returns the latest.

### Architectural Note: Compositor Pattern

DriveOrchestrator deliberately breaks the `record()` + `tick()` pattern that all
six social cognition orchestrators follow. It has no raw signals to accumulate —
the source orchestrators already handle signal accumulation. DriveOrchestrator is
a *compositor*: it reads derived state and synthesises a higher-order signal.
The `tick()` without `record()` is intentional, not an omission.

### Ordering Constraint

Drive ticks must run after source orchestrator ticks. The caller (typically
InnerLifeOrchestrator or a future scheduler) is responsible for this ordering.
This is the same pattern as MemoryHygieneScheduler calling
MemoryHygieneOrchestrator — the scheduler owns the sequence.

## Type System

### DriveAxis (enum)

```java
public enum DriveAxis {
    CURIOSITY,
    COMPETENCE,
    AFFILIATION,
    AUTONOMY
}
```

### DriveIntensity (record)

Per-axis intensity with provenance metadata.

```java
public record DriveIntensity(
        DriveAxis axis,
        double intensity,       // [0.0, 1.0]
        String trigger) {       // human-readable cause, e.g. "3 knowledge gaps in reflections"
    // compact constructor validates intensity range, non-null fields
}
```

### DriveSource (functional interface — SPI)

```java
@FunctionalInterface
public interface DriveSource {
    DriveIntensity evaluate(String agentId, String tenantId);
}
```

Each implementation takes its source orchestrator as a constructor dependency
and reads its public accessors. The interface is intentionally minimal — one
method, no lifecycle. DriveOrchestrator calls it; the source does the extraction.

### DriveProfile (record)

Composite motivational state — the cached output of `tick()`.

```java
public record DriveProfile(
        String agentId,
        String tenantId,
        Map<DriveAxis, DriveIntensity> drives,
        double compositeMotivation,     // [0.0, 1.0] — weighted aggregate
        DriveAxis dominantDrive,        // axis with highest modulated intensity
        Instant evaluatedAt) {
    // compact constructor validates, defensively copies map
}
```

### DriveTick (sealed interface)

```java
public sealed interface DriveTick {
    record NoChange(@Nullable String reason) implements DriveTick {}
    record Updated(DriveProfile previous, DriveProfile current,
                   List<DriveAxis> changed) implements DriveTick {}
}
```

`NoChange` when no axis intensity changed beyond a configurable threshold.
`Updated` carries both previous and current for delta comparison.

### DriveConfig (record)

```java
public record DriveConfig(
        Map<DriveAxis, Double> axisWeights,         // per-axis weight for composition
        double changeThreshold,                      // minimum delta to emit Updated vs NoChange
        double moodPleasureModulation,               // how much pleasure affects drive intensity
        double moodArousalModulation,                // how much arousal amplifies drives
        double personalityModulationStrength,        // overall trait modulation factor
        double maxIntensity,                         // upper bound after modulation
        double minIntensity) {                       // lower bound (floor)
    // compact constructor validates ranges; defaults() factory
    public static DriveConfig defaults() { ... }
}
```

## Drive Source Implementations

### CuriosityDrive

**Source:** MemoryHygieneOrchestrator (via HygieneTick data)
**Signal:** Knowledge gaps — weak or fragmented knowledge indicated by eviction
patterns and low retention scores

**API gap:** `HygieneTick.Completed` provides lifecycle data (evicted/consolidated
counts, `List<RetentionScore>`) but is oriented toward memory cleanup, not
epistemic assessment. CuriosityDrive needs a knowledge-gap signal: how much
knowledge is missing or weak.

**Required change:** Add a knowledge-gap accessor to `MemoryHygieneOrchestrator`:
```java
public KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId);
```
`KnowledgeGapSummary` is a new record derived from recent HygieneTick data:
count of low-retention memories, consolidation group diversity, recency of
last reflection. This is useful beyond drives — InnerLife topic selection
can use it to decide what the agent should think about.

**Extraction logic:** High count of low-retention memories = knowledge is
fragmented or poorly understood = higher curiosity. Diverse consolidation
groups = multiple knowledge areas need attention. Zero hygiene activity =
zero curiosity (no knowledge gaps detected).

```java
public class CuriosityDrive implements DriveSource {
    CuriosityDrive(MemoryHygieneOrchestrator hygieneOrchestrator)
    // evaluate(): read knowledgeGaps(), compute intensity from gap indicators
}
```

### CompetenceDrive

**Source:** StrategyLearningOrchestrator
**Signal:** Engagement dimension trends — declining or stagnant performance

**API gap:** `currentStrategy()` returns `StrategyProfile` with `dimensions()`
(Map<String, Double>) — a point-in-time snapshot. CompetenceDrive needs the
*derivative*: is engagement improving or declining? A low absolute score after
a sharp improvement is different from a low score after sustained decline.

**Required change:** Add engagement trend data to StrategyLearningOrchestrator:
```java
public Optional<EngagementTrend> engagementTrend(String agentId, String tenantId);
```
`EngagementTrend` is a new record: per-dimension trend direction (improving,
stable, declining), response rate trajectory, evidence window size. Derived
from the orchestrator's internal `AgentLearningState` counters.

**Extraction logic:** Declining dimensions = high competence drive (agent
needs to improve). Stable low dimensions = moderate drive. Improving
dimensions = low drive (progress is being made). No data = zero drive.

```java
public class CompetenceDrive implements DriveSource {
    CompetenceDrive(StrategyLearningOrchestrator strategyOrchestrator)
    // evaluate(): read engagementTrend(), compute intensity from declining dimensions
}
```

### AffiliationDrive

**Source:** UserModelOrchestrator
**Signal:** Familiarity decay across subjects — relationships losing strength

**API gap:** `currentProfile()` is per-subject. AffiliationDrive needs
cross-subject aggregation (relationship health across all known subjects).

**Required change:** Add a cross-subject accessor to `UserModelOrchestrator`:
```java
public List<UserProfile> activeProfiles(String agentId, String tenantId)
```
Returns all profiles for subjects the agent has interacted with. The
orchestrator already holds a `ConcurrentHashMap<String, SubjectState>` —
this accessor iterates the in-memory state.

**Extraction logic:** Scan active profiles for familiarity decay signals:
low `familiarityScore`, stale `lastInteraction`, regression in
`relationshipStage`. Subjects with recent healthy interactions reduce
affiliation intensity; neglected relationships increase it.

```java
public class AffiliationDrive implements DriveSource {
    AffiliationDrive(UserModelOrchestrator userModelOrchestrator,
                     double decayThreshold, Duration staleDuration)
    // evaluate(): scan all profiles, compute intensity from decay signals
}
```

### AutonomyDrive

**Source:** MentalModelOrchestrator
**Signal:** Intention misalignment — subjects whose projected intentions
conflict with the agent's established approach

**API gap:** `project()` is per-subject. AutonomyDrive needs cross-subject
aggregation of intention projections.

**Required change:** Add a cross-subject accessor to `MentalModelOrchestrator`:
```java
public List<MentalModelSnapshot> activeSnapshots(String agentId, String tenantId)
```
Returns all mental model snapshots for subjects the agent is tracking.

**Extraction logic:** Scan intention projections across subjects. Count
high-confidence projections where the subject's intentions (`BdiDimension.INTENTION`)
diverge from the agent's operational context. More conflicting intentions =
higher autonomy drive (the agent is being pushed in directions it didn't
choose). Zero projections = zero autonomy drive.

For Layer 1, "misalignment" is simplified to: count of high-confidence
intention projections, scaled by confidence. Full value-alignment detection
requires Layer 3's narrative identity (the agent needs to know what it
values to detect misalignment against those values).

```java
public class AutonomyDrive implements DriveSource {
    AutonomyDrive(MentalModelOrchestrator mentalModelOrchestrator,
                  double confidenceFloor)
    // evaluate(): scan projections, compute intensity from intention count × confidence
}
```

## DriveOrchestrator

```java
@ApplicationScoped
public class DriveOrchestrator {
    @Inject
    public DriveOrchestrator(
            CuriosityDrive curiosity,
            CompetenceDrive competence,
            AffiliationDrive affiliation,
            AutonomyDrive autonomy,
            MoodOrchestrator moodOrchestrator,
            DriveComposer composer,
            DriveConfig config) { ... }

    // Package-private test constructor with Clock
    DriveOrchestrator(..., Clock clock) { ... }

    // tick takes AgentDescriptor — same pattern as PersonalityEvolutionOrchestrator
    public DriveTick tick(String agentId, String tenantId,
                          AgentDescriptor descriptor) { ... }

    // Read cached state
    public Optional<DriveProfile> currentDrives(String agentId, String tenantId) { ... }
}
```

The tick signature takes `AgentDescriptor` because personality state is carried
on `descriptor.disposition()` — the same pattern used by
`PersonalityEvolutionOrchestrator.tick()` and `InnerLifeOrchestrator.tick()`.

### tick() Flow

1. Call each `DriveSource.evaluate(agentId, tenantId)` → four `DriveIntensity` values
2. Read `moodOrchestrator.currentMood(agentId, tenantId)` → `Optional<MoodState>`
3. Extract `AgentDisposition` from `descriptor.disposition()`
4. Delegate to `driveComposer.compose(rawDrives, disposition, moodState, config)` → `DriveProfile`
5. Compare with cached profile; return `DriveTick.Updated` or `DriveTick.NoChange`
6. Cache the new profile

## DriveComposer

Separate class owning the modulation and composition algebra. DriveOrchestrator
manages the lifecycle (tick/cache); DriveComposer owns the math.

```java
public class DriveComposer {
    public DriveProfile compose(
            Map<DriveAxis, DriveIntensity> rawDrives,
            @Nullable AgentDisposition disposition,
            @Nullable MoodState mood,
            DriveConfig config,
            String agentId, String tenantId, Instant now) { ... }
}
```

### Modulation

`compose()` applies two modulation layers to the raw intensities:

1. **Mood modulation:** From `MoodState` (PAD model, [-1, 1] axes).
   - Positive pleasure amplifies affiliation/competence drives.
   - Negative pleasure dampens all drives (low mood = low motivation).
   - High arousal amplifies all drives (heightened state = stronger motivation).
   - Low dominance amplifies autonomy drive (feeling controlled = want autonomy).
   - Null mood = no modulation (agent has no mood state yet).

2. **Personality modulation:** From `AgentDisposition` (eidos disposition profile).
   - Agreeable personality amplifies affiliation drive.
   - Conscientious personality amplifies competence drive.
   - Open personality amplifies curiosity drive.
   - Independent personality amplifies autonomy drive.
   - Modulation strength configurable via `DriveConfig.personalityModulationStrength`.
   - Null disposition = no modulation (agent has no personality traits yet).

Modulated intensity is clamped to `[minIntensity, maxIntensity]` from config.

### Composition

Weighted sum of modulated intensities using `DriveConfig.axisWeights`:

```
compositeMotivation = Σ(axisWeight[i] × modulatedIntensity[i]) / Σ(axisWeight[i])
```

`dominantDrive` is the axis with the highest modulated intensity.

## Upstream Changes Required

| Repo | Change | Why |
|------|--------|-----|
| blocks | Add `KnowledgeGapSummary knowledgeGaps(agentId, tenantId)` to `MemoryHygieneOrchestrator` | CuriosityDrive needs epistemic gap signals |
| blocks | Add `KnowledgeGapSummary` record | New type for knowledge gap assessment |
| blocks | Add `Optional<EngagementTrend> engagementTrend(agentId, tenantId)` to `StrategyLearningOrchestrator` | CompetenceDrive needs trend derivative |
| blocks | Add `EngagementTrend` record | New type for engagement trend direction |
| blocks | Add `List<UserProfile> activeProfiles(agentId, tenantId)` to `UserModelOrchestrator` | AffiliationDrive needs cross-subject data |
| blocks | Add `List<MentalModelSnapshot> activeSnapshots(agentId, tenantId)` to `MentalModelOrchestrator` | AutonomyDrive needs cross-subject data |

All changes are additive — no existing APIs are modified. Personality modulation
reads `AgentDescriptor.disposition()` (eidos-api), which is already available as
a tick parameter — no eidos changes needed.

## Package Structure

All new types live in `io.casehub.blocks.agentic.social.drive`:

```
blocks/src/main/java/io/casehub/blocks/agentic/social/drive/
├── DriveAxis.java
├── DriveIntensity.java
├── DriveSource.java
├── DriveProfile.java
├── DriveTick.java
├── DriveConfig.java
├── DriveOrchestrator.java
├── DriveComposer.java
├── KnowledgeGapSummary.java
├── EngagementTrend.java
├── CuriosityDrive.java
├── CompetenceDrive.java
├── AffiliationDrive.java
└── AutonomyDrive.java

blocks/src/test/java/io/casehub/blocks/agentic/social/drive/
├── DriveOrchestratorTest.java
├── DriveComposerTest.java
├── CuriosityDriveTest.java
├── CompetenceDriveTest.java
├── AffiliationDriveTest.java
└── AutonomyDriveTest.java
```

Sub-package under `social` rather than sibling to it — drives are part of the
social cognition module, not a separate concern.

## Testing Strategy

- **Per-drive unit tests:** Each drive source tested independently with a mocked
  source orchestrator. Verify intensity computation from various data states
  (empty, nominal, extreme, edge cases).
- **DriveComposer tests:** Pure unit tests with known raw intensities, mood
  states, and dispositions. Verify modulation math, weighted composition,
  dominant drive selection. Test null mood/disposition (no modulation).
- **DriveOrchestrator tests:** Mocked drive sources and DriveComposer. Verify
  tick lifecycle, caching, change threshold (NoChange vs Updated), state
  key management.
- **Integration test:** Full tick cycle with real (in-memory) orchestrators.
  Record signals, tick source orchestrators, tick DriveOrchestrator, verify
  drive profile reflects the signals.

All tests are plain JUnit 5 with Mockito (no Quarkus runtime), consistent
with the blocks testing pattern.

## Follow-up Issues

| Title | Scope |
|-------|-------|
| InnerLife reads DriveProfile for proactive initiation | MotivationAssessment evolves to use drive intensity |
| DriveStore persistence for Layer 2 consumption | Add optional persistence when GoalProposer needs drive history |
| Narrative feedback into drive modulation (Layer 3) | NarrativeOrchestrator feeds back into drive system |

## References

- [Research blog 2026-08-21-mdp04] — three-layer vision, drive-to-orchestrator mapping
- [GE-20260811-e941cc] — AgentDisposition vs DispositionProfile type split
- [GE-20260729-172d18] — check-before-creating SPIs pattern
- [Deci & Ryan, 2000] — Self-Determination Theory (autonomy, competence, relatedness — three axes)
- [Oudeyer & Kaplan, 2007] — intrinsic motivation and curiosity in developmental robotics
- [Berlyne, 1954] — curiosity as a drive state
- [Issue #129] — epic with six children
- [Issue #126] — social cognition orchestrators (foundation)
- `MotivationAssessment.java` — existing package-private record in InnerLifeOrchestrator
- `PersonalityEvolutionOrchestrator.java` — bounded trait drift, AgentDescriptor tick param
- `MoodOrchestrator.java` — PAD emotional state, no-store pattern
- `MoodState` (neocortex-memory-api) — pleasure, arousal, dominance in [-1, 1]
- `StrategyLearningOrchestrator.java` — engagement dimensions, currentStrategy accessor
- `StrategyProfile.java` — dimensions Map<String, Double>, point-in-time snapshot
- `UserModelOrchestrator.java` — per-subject profiles, familiarity scoring
- `UserProfile.java` — familiarityScore, relationshipStage, signal counts
- `MentalModelOrchestrator.java` — BDI projections, per-subject snapshots
- `MentalProjection.java` — conditionKey, value, confidence, BdiDimension
- `MentalModelSnapshot.java` — beliefs, desires, intentions as List<AttributedState>
- `AgentDescriptor.disposition()` (eidos-api) — AgentDisposition with per-axis profile values
- `TraitPressureSource<E>` — push-based source SPI precedent in personality evolution
