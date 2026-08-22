# DriveProfile Prompt Enrichment — Design Spec

**Issue:** casehubio/blocks#149
**Date:** 2026-08-22
**Branch:** issue-149-drive-prompt-enrichment
**Repos:** blocks (primary)

## Overview

Render `DriveProfile` as a cognitive observation section in agent LLM prompts
and wire `DriveOrchestrator.tick()` into the existing agent tick lifecycle via
`InnerLifeOrchestrator`.

This is the observability bridge between Drive Architecture (#129, Layer 1)
and the agent's self-awareness. The agent sees its motivational state but
does not act on it autonomously — that is Layer 2 (#136).

Blocks provides the rendering capability and lifecycle wiring. Downstream
apps (quarkmind, etc.) wire the rendered section into their
`WorldObservationProvider` implementation — that is consumer-side work,
not part of this issue.

## 1. Observation Rendering

### New method on `CognitiveObservationSections`

```java
public static ObservationSection motivationalStateSection(DriveProfile profile)
```

Returns an `ObservationSection.ItemList` with header `"Motivational State"`.

**Per-axis rendering:** Each axis with intensity >= 0.05 produces one item:
```
Curiosity: 0.6 — 5 low-retention memories across 3 groups
```

Format: `<axis display name>: <intensity formatted to 1dp> — <trigger>`

**Axis display names:** `DriveAxis.CURIOSITY` → `"Curiosity"`, etc. Title case
of the enum name. Computed via `axis.name().charAt(0) + axis.name().substring(1).toLowerCase()`.

**Zero filtering:** Axes with `intensity < 0.05` are omitted (values that
display as "0.0" after 1dp formatting are excluded — mood/personality
modulation can produce very small positive values from zero raw intensity).
If ALL axes are below threshold, return an `ItemList` with empty items and
emptyMessage `"No active drives."`.

**Axis ordering:** `DriveAxis.values()` order (CURIOSITY, COMPETENCE,
AFFILIATION, AUTONOMY) — stable and matches the enum declaration.

**Null profile handling:** The caller passes a non-null `DriveProfile`.
`DriveOrchestrator.currentDrives()` returns `Optional<DriveProfile>` —
the caller skips the section when empty. No null handling inside the method.

### Package placement

Same class: `io.casehub.blocks.summarisation.observation.affordance.CognitiveObservationSections`.
Follows the established pattern — goals, activity, experience, insights,
and relationship notes are all factory methods on this class.

### New dependency

`CognitiveObservationSections` gains a compile dependency on
`io.casehub.blocks.agentic.social.drive.DriveProfile` and `DriveAxis`.
Both are in the same module (blocks) — no new Maven dependencies.

## 2. Tick Lifecycle Wiring

### CDI enablement — DriveOrchestrator only

Drive sources are **not** CDI beans. DriveOrchestrator constructs them
internally from injected source orchestrators and DriveConfig. This avoids
expanding the CDI surface to four implementation classes and handles the
`MemoryHygieneOrchestrator` gap (not CDI-managed — 11-param constructor
with SPI types, out of scope to CDI-enable).

| Class | Annotation | Constructor changes |
|-------|------------|-------------------|
| `DriveOrchestrator` | `@ApplicationScoped` | New `@Inject` CDI constructor (see below); keep existing constructor + test constructor |
| `DriveComposer` | `@ApplicationScoped` | No changes (implicit no-arg constructor) |

**DriveOrchestrator CDI constructor:**

```java
@Inject
public DriveOrchestrator(
        Instance<MemoryHygieneOrchestrator> hygieneInstance,
        StrategyLearningOrchestrator strategy,
        UserModelOrchestrator userModel,
        MentalModelOrchestrator mentalModel,
        MoodOrchestrator mood,
        DriveComposer composer,
        DriveConfig config) {
    this(
        hygieneInstance.isResolvable()
            ? new CuriosityDrive(hygieneInstance.get())
            : (agentId, tenantId) -> new DriveIntensity(
                    DriveAxis.CURIOSITY, 0.0, "no hygiene orchestrator"),
        new CompetenceDrive(strategy),
        new AffiliationDrive(userModel,
                config.affiliationDecayThreshold(),
                config.affiliationStaleDuration()),
        new AutonomyDrive(mentalModel, config.autonomyConfidenceFloor()),
        mood, composer, config
    );
}
```

Key points:
- `Instance<MemoryHygieneOrchestrator>` — optional injection. When the
  consumer doesn't provide a CDI bean for MemoryHygieneOrchestrator (which
  requires 11 params including SPIs), curiosity drive returns zero.
- Per-drive config params (`decayThreshold`, `staleDuration`,
  `confidenceFloor`) sourced from `DriveConfig` — not hardcoded.
- Existing constructor (4 drives + mood + composer + config) preserved
  for direct construction and testing.

Per `GE-20260529-ab148d`: exactly one constructor has `@Inject`. The
existing constructors remain unannotated.

### DriveConfig extension

Add three fields to `DriveConfig` for drive-source configuration:

```java
public record DriveConfig(
        Map<DriveAxis, Double> axisWeights,
        double changeThreshold,
        double moodPleasureModulation,
        double moodArousalModulation,
        double personalityModulationStrength,
        double maxIntensity,
        double minIntensity,
        // new — drive source params
        double affiliationDecayThreshold,    // default: 0.5
        Duration affiliationStaleDuration,   // default: 24h
        double autonomyConfidenceFloor       // default: 0.6
) {
    public static DriveConfig defaults() {
        // updated to include new fields with defaults
    }
}
```

No `@Produces @DefaultBean` — follows the existing pattern where downstream
consumers provide config records as CDI beans. Other config records
(`InnerLifeConfig`, `MoodConfig`, `UserModelConfig`, etc.) follow the same
convention.

### InnerLifeOrchestrator integration

Add `DriveOrchestrator` as an `@Inject` dependency of `InnerLifeOrchestrator`.
At the start of `doTick()`, call:

```java
driveOrchestrator.tick(descriptor.agentId(), descriptor.tenancyId(), descriptor);
```

This ensures drives are computed before any inner life logic runs. The return
value (`DriveTick`) is ignored for now — the follow-up issue (InnerLife
MotivationAssessment integration) will use it.

**Intentional short-term coupling:** Embedding drive ticks inside
InnerLifeOrchestrator is a pragmatic stepping stone, not a permanent design.
When Layer 2 (#136, GoalProposer) arrives, drives will need to be computed
independently of inner life — extraction into a dedicated tick coordinator
will be required at that point.

### Ordering guarantee

Drive sources read state from their source orchestrators (MemoryHygiene,
StrategyLearning, UserModel, MentalModel). Those orchestrators must have been
ticked before `DriveOrchestrator.tick()`. The same caller that ticks
InnerLifeOrchestrator is responsible for ticking source orchestrators first —
this ordering is already required and documented in the #129 design spec.
InnerLifeOrchestrator sits at the end of the tick chain, so wiring drives
inside it preserves the constraint.

## 3. Testing Strategy

### Rendering tests

Add to `CognitiveObservationSectionsTest`:

- `motivationalStateSection_renders_non_zero_axes` — profile with mixed
  intensities → ItemList with correct format and ordering
- `motivationalStateSection_filters_low_intensity_axes` — profile with
  axes below 0.05 threshold → only above-threshold appear
- `motivationalStateSection_all_below_threshold_shows_no_active_drives` —
  all axes below 0.05 → empty items with emptyMessage
- `motivationalStateSection_single_axis_non_zero` — one axis non-zero →
  single item
- `motivationalStateSection_boundary_at_threshold` — axis at exactly 0.05
  → included; axis at 0.04 → excluded

### Lifecycle tests

Extend `InnerLifeOrchestratorTest`:

- Verify `DriveOrchestrator.tick()` is called when `InnerLifeOrchestrator.tick()`
  is called — mock DriveOrchestrator, verify invocation with correct args
- Verify drive profile is available via `currentDrives()` after tick

### DriveOrchestrator CDI constructor test

- Test with `MemoryHygieneOrchestrator` available → CuriosityDrive evaluates
  normally
- Test without `MemoryHygieneOrchestrator` → CuriosityDrive returns zero
- Verify drive-source params are sourced from DriveConfig

### End-to-end rendering test

- Construct a `DriveProfile`, pass to `motivationalStateSection()`, render
  via `AffordanceRenderer.renderObservation()`, verify output matches expected
  format including section header

## 4. Scope Boundaries

**In scope:**
- `motivationalStateSection()` factory method on `CognitiveObservationSections`
- CDI enablement for `DriveOrchestrator` and `DriveComposer`
- `DriveConfig` extended with drive-source params
- `DriveOrchestrator.tick()` wired into `InnerLifeOrchestrator.doTick()`
- Tests for all of the above

**Out of scope:**
- Downstream `WorldObservationProvider` integration — consumers call
  `motivationalStateSection()` in their implementations (consumer-side)
- CDI enablement of `MemoryHygieneOrchestrator` (11-param constructor,
  separate concern)
- InnerLife MotivationAssessment integration (separate follow-up)
- DriveStore persistence
- Narrative feedback into drive modulation (Layer 3)

## 5. Files Changed

| File | Change |
|------|--------|
| `CognitiveObservationSections.java` | Add `motivationalStateSection(DriveProfile)` |
| `DriveOrchestrator.java` | `@ApplicationScoped`, new `@Inject` CDI constructor |
| `DriveComposer.java` | `@ApplicationScoped` |
| `DriveConfig.java` | Add affiliationDecayThreshold, affiliationStaleDuration, autonomyConfidenceFloor |
| `InnerLifeOrchestrator.java` | Inject `DriveOrchestrator`, call `tick()` in `doTick()` |
| `CognitiveObservationSectionsTest.java` | Add motivationalState tests |
| `InnerLifeOrchestratorTest.java` | Add drive tick lifecycle test |
| `DriveOrchestratorTest.java` | Add CDI constructor tests |
| `DriveConfigTest.java` | Update for new fields |

## References

- `CognitiveObservationSections.java` — existing factory method pattern
- `ObservationSection.java` — sealed interface (ItemList variant)
- `AffordanceRenderer.java:42` — `renderObservation()` consumes sections
- `DriveOrchestrator.java` — tick lifecycle, currentDrives() accessor
- `DriveProfile.java` — composite motivational state record
- `DriveIntensity.java` — per-axis intensity with trigger provenance
- `InnerLifeOrchestrator.java:84` — tick() entry point, doTick() implementation
- `MemoryHygieneOrchestrator.java:27` — not CDI-managed (11-param constructor)
- `InnerLifeConfig.java` — config record pattern (no @DefaultBean, consumer provides)
- Issue #129 design spec — ordering constraint, Layer 2 extraction note
- `GE-20260529-ab148d` — multiple CDI constructors require exactly one @Inject
- Review R1-02 — MemoryHygieneOrchestrator CDI gap → Instance<> solution
- Review R1-07 — zero-filtering threshold 0.05 to avoid displaying "0.0"
