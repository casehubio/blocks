# Narrative Identity + Social Emergence — Design Spec

**Issue:** casehubio/blocks#142
**Date:** 2026-08-24
**Branch:** issue-142-narrative-identity
**Repos:** blocks (primary), engine-api (GoalPriorityEscalationPolicy)

## Overview

Layer 3 of the autonomous intelligence research direction. Two research threads:

1. **Narrative Identity** — agents construct and maintain a first-person autobiography
   from accumulated experience. The narrative shapes future drive intensity through
   thematic modulation in DriveComposer.

2. **Social Emergence** — collective behaviors emerge from multi-agent interaction:
   norms detected from repeated interaction patterns, shared goals from aligned
   drives, group identity from collective experience.

Both threads compose with the existing social cognition stack (7 orchestrators),
Drive Architecture (#129), and Autonomous Goal Generation (#136). Both follow the
compositor pattern (ADR-0001) for their orchestrator components. Both use CBR-backed
stores for persistence.

### What #142 delivers

Design spec + foundation types/SPIs for all six children. No orchestrator
implementation — children (#143-#148) build on these types.

## Architecture

```
  Scheduler (consumer — quarkmind, claudony)
  │
  │  Tick ordering (D10):
  │    1. Source orchestrators (Personality, Memory, Strategy, UserModel, MentalModel, Mood)
  │    2. NarrativeSynthesiser (if composite gate triggers — D6)
  │    3. NarrativeOrchestrator (compositor — reads NarrativeStore)
  │    4. DriveOrchestrator (reads narrative modulation coefficients — D4)
  │    5. GoalProposalOrchestrator
  │
  │  Ordering rationale: narrative must tick before drives so DriveComposer
  │  sees the current narrative modulation. NarrativeOrchestrator sees current
  │  reflections but previous-cycle drives — acceptable because narrative
  │  changes slowly (gated by composite trigger).
  │
  │  Note: InnerLifeOrchestrator.doTick() currently calls driveOrchestrator.tick()
  │  inline (line 102). This is superseded by the scheduler ordering — consumers
  │  must remove the inline drive tick from InnerLifeOrchestrator and place it
  │  in the scheduler at step 4.
  │
  ├─── NARRATIVE IDENTITY ──────────────────────────────────────────────
  │
  │  NarrativeSynthesiser (effectful, scheduled — D3)
  │  │  reads: ReflectionQueryStore (new SPI — in blocks, alongside ReflectionStore)
  │  │  calls: AgentProvider (LLM synthesis)
  │  │  writes: NarrativeStore
  │  │  trigger: composite gate (count + novelty + quiet — D6)
  │  │
  │  NarrativeStore SPI (read + write — #144)
  │  │  NoOpNarrativeStore @DefaultBean (CbrNarrativeStore provided by #144)
  │  │  persist: NarrativeState (fragments: episodes + themes)
  │  │
  │  NarrativeOrchestrator (compositor — #143)
  │  │  tick(): reads NarrativeStore, caches NarrativeState
  │  │  currentNarrative(): returns cached state
  │  │  NO LLM, NO side effects
  │  │
  │  NarrativeFeedback (DriveComposer modulation — #145)
  │     reads: NarrativeState from NarrativeOrchestrator
  │     modulates: all 4 drive axes via DerivedTheme salience weights
  │
  ├─── SOCIAL EMERGENCE ────────────────────────────────────────────────
  │
  │  NormObservationRecorder (effectful — records interaction patterns)
  │  │  implements: ConversationListener or MessageObserver
  │  │  writes: CbrCaseMemoryStore (norm observation case type)
  │  │
  │  SocialNormDetector (compositor — #146)
  │  │  tick(): reads norm observation cases from CBR store
  │  │  output: DetectedNorms (frequency + consistency analysis)
  │  │
  │  CollectiveGoalFormation (compositor — #147)
  │  │  tick(): reads N agents' DriveProfiles
  │  │  agent set: provided by consumer at construction (D12)
  │  │  output: JointIntention proposals when drive alignment detected
  │  │
  │  GroupNarrativeOrchestrator (compositor — #148)
  │     reads: group interaction history, collective episodes
  │     shared types: NarrativeFragment sealed hierarchy (D5)
  │     output: group-scoped NarrativeState
  │
  ├─── LAYER 2 DEFERRALS (D11) ────────────────────────────────────────
  │
  │  Cross-axis goal composition
  │     DerivedTheme with multiple significant axis weights → compound goal
  │  Governed priority escalation
  │     GoalPriorityEscalationPolicy SPI (engine-api)
  │     NarrativeState provides provenance for SECONDARY → PRIMARY
  │
  └─────────────────────────────────────────────────────────────────────
```

### Split Architecture — NarrativeSynthesiser + NarrativeOrchestrator (D3)

LLM synthesis (producing episodes and themes from reflections) is non-deterministic
and expensive. Placing it in a compositor tick() would break ADR-0001's guarantees.
The split mirrors the MemoryHygieneScheduler / MemoryHygieneOrchestrator pattern:

| Component | Role | Side effects |
|-----------|------|-------------|
| NarrativeSynthesiser | Effectful — calls LLM, writes to NarrativeStore | Yes (LLM, I/O) |
| NarrativeStore | Persistence SPI — read + write NarrativeState | Yes (I/O) |
| NarrativeOrchestrator | Compositor — reads NarrativeStore, caches, exposes accessor | None |

The consumer's scheduler calls `NarrativeSynthesiser.synthesiseIfNeeded()` in the
tick ordering (step 2), then `NarrativeOrchestrator.tick()` (step 3) reads the
latest state from the store. The orchestrator's tick() is deterministic, fast, and
side-effect-free.

### Composite Synthesis Gate (D6)

NarrativeSynthesiser checks whether synthesis is warranted before calling the LLM:

```java
public record NarrativeSynthesisGate(
        int minNewReflections,         // count gate
        double noveltyThreshold,       // quality gate — Jaccard distance vs current narrative
        Duration quietPeriodBypass) {  // inactivity override

    public static NarrativeSynthesisGate defaults() {
        return new NarrativeSynthesisGate(5, 0.3, Duration.ofMinutes(120));
    }
}
```

Three mechanisms, matching InnerLifeConfig.ContentQualityGate:
1. **Count gate** — at least N new reflections since last synthesis
2. **Quality gate** — at least one reflection scores above novelty threshold
   (TokenJaccardDistance vs current narrative text)
3. **Inactivity bypass** — re-synthesise after quiet period regardless of count

## Type System

### Package: `io.casehub.blocks.agentic.social.narrative`

#### NarrativeFragment (sealed interface)

Base type for all narrative elements — individual episodes, group episodes, and
derived themes. The sealed hierarchy enables composition across individual and group
scope while allowing scope-specific fields.

```java
public sealed interface NarrativeFragment
        permits IndividualEpisode, GroupEpisode, DerivedTheme {

    String id();
    Instant from();
    @Nullable Instant to();
    List<String> thematicTags();
}
```

#### IndividualEpisode (record)

A significant personal experience with emotional valence. Raw material for theme
derivation. Produced by NarrativeSynthesiser from reflections.

```java
public record IndividualEpisode(
        String id,
        Instant from,
        @Nullable Instant to,
        List<String> thematicTags,
        String description,
        double emotionalValence,        // [-1.0, 1.0]
        List<String> sourceReflectionIds
) implements NarrativeFragment {
    public IndividualEpisode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(description);
        if (emotionalValence < -1.0 || emotionalValence > 1.0)
            throw new IllegalArgumentException("emotionalValence must be in [-1, 1]");
        sourceReflectionIds = List.copyOf(sourceReflectionIds);
    }
}
```

#### GroupEpisode (record)

A collective experience shared by multiple agents. Tracks membership at the time
of the episode, role attributions (who did what), and consensus level (how
agreed-upon the episode is). Produced by GroupNarrativeOrchestrator (#148).

```java
public record GroupEpisode(
        String id,
        Instant from,
        @Nullable Instant to,
        List<String> thematicTags,
        String description,
        double emotionalValence,        // [-1.0, 1.0]
        Set<String> membershipAtTime,
        Map<String, String> roleAttributions,  // agentId → role description
        double consensusLevel           // [0.0, 1.0]
) implements NarrativeFragment {
    public GroupEpisode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(description);
        if (emotionalValence < -1.0 || emotionalValence > 1.0)
            throw new IllegalArgumentException("emotionalValence must be in [-1, 1]");
        membershipAtTime = Set.copyOf(membershipAtTime);
        roleAttributions = Map.copyOf(roleAttributions);
        if (consensusLevel < 0.0 || consensusLevel > 1.0)
            throw new IllegalArgumentException("consensusLevel must be in [0, 1]");
    }
}
```

#### DerivedTheme (record)

A narrative theme derived from episodes — typed with salience score and per-axis
drive modulation weights. Themes bridge narrative identity to drive modulation:
each theme amplifies or dampens specific drive axes based on story relevance.

```java
public record DerivedTheme(
        String id,
        Instant from,
        @Nullable Instant to,
        List<String> thematicTags,
        String label,                   // e.g., "crisis-helper", "technical-expert"
        double salience,                // [0.0, 1.0] — how central to identity
        Map<DriveAxis, Double> axisModulationWeights,  // per-axis amplification [-1, 1]
        List<String> supportingFragmentIds
) implements NarrativeFragment {
    public DerivedTheme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(from);
        thematicTags = List.copyOf(thematicTags);
        Objects.requireNonNull(label);
        if (salience < 0.0 || salience > 1.0)
            throw new IllegalArgumentException("salience must be in [0, 1]");
        axisModulationWeights = Map.copyOf(axisModulationWeights);
        for (var w : axisModulationWeights.values()) {
            if (w < -1.0 || w > 1.0)
                throw new IllegalArgumentException("axis modulation weight must be in [-1, 1]");
        }
        supportingFragmentIds = List.copyOf(supportingFragmentIds);
    }
}
```

Modulation semantics for `axisModulationWeights`:
- Positive weight (+0.3 on AFFILIATION) = narrative amplifies this drive
- Negative weight (-0.2 on AUTONOMY) = narrative dampens this drive
- Zero or absent = no modulation from this theme
- Multiple themes compose additively (weighted by salience)

#### NarrativeScope (enum)

```java
public enum NarrativeScope {
    INDIVIDUAL,
    GROUP
}
```

#### NarrativeState (record)

Composite narrative state — the cached output of NarrativeOrchestrator.tick().
Supports both individual agents (scopeId = agentId) and groups (scopeId = groupId).
Provides typed accessors for episodes, themes, and the dominant theme.

```java
public record NarrativeState(
        String scopeId,                 // agentId for INDIVIDUAL, groupId for GROUP
        String tenantId,
        NarrativeScope scope,
        List<NarrativeFragment> fragments,
        Instant synthesisedAt,
        int reflectionCountAtSynthesis
) {
    public NarrativeState {
        Objects.requireNonNull(scopeId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(scope);
        fragments = List.copyOf(fragments);
        Objects.requireNonNull(synthesisedAt);
    }

    public List<IndividualEpisode> episodes() {
        return fragments.stream()
                .filter(IndividualEpisode.class::isInstance)
                .map(IndividualEpisode.class::cast)
                .toList();
    }

    public List<GroupEpisode> groupEpisodes() {
        return fragments.stream()
                .filter(GroupEpisode.class::isInstance)
                .map(GroupEpisode.class::cast)
                .toList();
    }

    public List<DerivedTheme> themes() {
        return fragments.stream()
                .filter(DerivedTheme.class::isInstance)
                .map(DerivedTheme.class::cast)
                .toList();
    }

    public @Nullable DerivedTheme dominantTheme() {
        return themes().stream()
                .max(Comparator.comparingDouble(DerivedTheme::salience))
                .orElse(null);
    }
}
```

#### NarrativeConfig (record)

```java
public record NarrativeConfig(
        NarrativeSynthesisGate synthesisGate,
        int maxEpisodes,               // cap on retained episodes
        int maxThemes,                 // cap on active themes
        double themeSalienceFloor,     // themes below this are pruned
        int maxReflectionsPerSynthesis // input cap for LLM
) {
    public static NarrativeConfig defaults() {
        return new NarrativeConfig(
                NarrativeSynthesisGate.defaults(),
                50, 10, 0.1, 20);
    }
}
```

#### NarrativeTick (sealed interface)

```java
public sealed interface NarrativeTick {
    record NoChange(@Nullable String reason) implements NarrativeTick {}
    record Updated(NarrativeState previous, NarrativeState current,
                   List<String> newEpisodeIds,
                   List<String> newThemeLabels) implements NarrativeTick {}
}
```

#### NarrativeStore (SPI)

Persistence for NarrativeState. Read + write (unlike ReflectionStore which is
write-only). CbrNarrativeStore is the @DefaultBean.

```java
public interface NarrativeStore {
    void store(NarrativeState state);
    @Nullable NarrativeState load(String scopeId, String tenantId);
}
```

#### NarrativeSynthesiser

Effectful component that transforms reflections into narrative. Called by the
consumer's scheduler, not by the orchestrator.

```java
public class NarrativeSynthesiser {

    // Constructor: AgentProvider, NarrativeStore, ReflectionQueryStore,
    //              NarrativeConfig, Clock

    public NarrativeSynthesisTick synthesiseIfNeeded(
            String agentId, String tenantId) { ... }
}
```

`NarrativeSynthesisTick` is sealed:
```java
public sealed interface NarrativeSynthesisTick {
    record Skipped(String reason) implements NarrativeSynthesisTick {}
    record Synthesised(NarrativeState state,
                       int newReflectionsConsumed) implements NarrativeSynthesisTick {}
}
```

#### NarrativeOrchestrator

Compositor — reads from NarrativeStore, caches, exposes accessor. No LLM, no
side effects.

```java
@ApplicationScoped
public class NarrativeOrchestrator {

    private final NarrativeStore store;
    private final ConcurrentHashMap<String, NarrativeState> cache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks =
            new ConcurrentHashMap<>();

    @Inject
    public NarrativeOrchestrator(NarrativeStore store) { ... }

    public NarrativeTick tick(String agentId, String tenantId) {
        // 1. Lock per agent
        // 2. Load from store
        // 3. Compare with cached state
        // 4. Return Updated or NoChange
        // 5. Update cache
    }

    public Optional<NarrativeState> currentNarrative(
            String agentId, String tenantId) {
        return Optional.ofNullable(cache.get(agentId + ":" + tenantId));
    }
}
```

### DriveComposer Extension (D4)

DriveComposer.compose() gains a `@Nullable Map<DriveAxis, Double>` parameter —
pre-computed narrative modulation coefficients per axis. DriveComposer does NOT
depend on NarrativeState or any narrative types — the conversion happens in the
caller. This preserves unidirectional dependency: narrative → drive (DerivedTheme
uses DriveAxis), but drive never → narrative.

```java
public DriveProfile compose(
        Map<DriveAxis, DriveIntensity> rawDrives,
        @Nullable AgentDisposition disposition,
        @Nullable MoodState mood,
        @Nullable Map<DriveAxis, Double> narrativeModulation,  // NEW — pre-computed coefficients
        DriveConfig config,
        String agentId, String tenantId, Instant now) { ... }
```

**Modulation conversion** (in DriveOrchestrator or a utility in the narrative package):

```java
static Map<DriveAxis, Double> computeNarrativeModulation(NarrativeState narrative) {
    var modulation = new EnumMap<DriveAxis, Double>(DriveAxis.class);
    for (var theme : narrative.themes()) {
        for (var entry : theme.axisModulationWeights().entrySet()) {
            modulation.merge(entry.getKey(),
                    theme.salience() * entry.getValue(), Double::sum);
        }
    }
    return modulation;
}
```

**DriveComposer modulation algebra:**
```
modulatedIntensity += narrativeModulation.getOrDefault(axis, 0.0)
                      × config.narrativeModulationStrength()
```

Null map = no modulation. Existing callers pass null. Future modulation sources
(e.g., social norms influencing drives) plug in the same way — no DriveComposer
change needed.

**DriveOrchestrator integration:** DriveOrchestrator gains `Instance<NarrativeOrchestrator>`
as a constructor dependency (using `Instance<>` for optional resolution). In tick():
```java
Map<DriveAxis, Double> narrativeMod = narrativeOrchestrator.stream().findFirst()
        .flatMap(no -> no.currentNarrative(agentId, tenantId))
        .map(NarrativeModulation::compute)
        .orElse(null);
composer.compose(rawDrives, disposition, mood, narrativeMod, config, agentId, tenantId, now);
```

### CognitiveObservationSections Extension

New factory method for rendering narrative in agent prompts:

```java
public static ObservationSection narrativeSection(NarrativeState state) {
    var items = new ArrayList<String>();
    var dominant = state.dominantTheme();
    if (dominant != null) {
        items.add("Core identity: " + dominant.label()
                + " (salience: " + String.format("%.1f", dominant.salience()) + ")");
    }
    for (var episode : state.episodes()) {
        if (episode.emotionalValence() > 0.3 || episode.emotionalValence() < -0.3) {
            items.add("Memory: " + episode.description());
        }
    }
    for (var theme : state.themes()) {
        if (!theme.equals(dominant) && theme.salience() >= 0.3) {
            items.add("Theme: " + theme.label());
        }
    }
    if (items.isEmpty()) {
        return ObservationSection.items("Self-Narrative", "No established identity yet.", List.of());
    }
    return ObservationSection.items("Self-Narrative", null, items);
}
```

### ReflectionQueryStore (blocks)

NarrativeSynthesiser needs to read stored reflections. ReflectionStore is write-only.
A new read SPI in blocks alongside ReflectionStore and ReflectionEntry (all three
are in blocks — placing it in neocortex-memory-api would create a circular
dependency since ReflectionEntry is a blocks type):

```java
public interface ReflectionQueryStore {
    List<ReflectionEntry> findSince(String agentId, String tenantId, Instant since);
    int countSince(String agentId, String tenantId, Instant since);
}
```

`NoOpReflectionQueryStore` @DefaultBean returns empty lists. Consumers that enable
reflection persistence provide a real implementation.

### Package: `io.casehub.blocks.agentic.social.emergence`

#### SocialNorm (record)

A detected behavioral pattern that has become normative — agents consistently
follow it without explicit programming.

```java
public record SocialNorm(
        String normId,
        String description,            // human-readable: "verify before escalating"
        String behavioralPattern,       // structured: action sequence or rule
        double adherenceRate,           // [0, 1] — how consistently followed
        int observationCount,           // how many times observed
        Set<String> participatingAgents,
        Instant firstObserved,
        Instant lastObserved,
        NormStrength strength           // EMERGING, ESTABLISHED, DECLINING
) {
    public SocialNorm {
        Objects.requireNonNull(normId);
        Objects.requireNonNull(description);
        Objects.requireNonNull(behavioralPattern);
        if (adherenceRate < 0.0 || adherenceRate > 1.0)
            throw new IllegalArgumentException("adherenceRate must be in [0, 1]");
        participatingAgents = Set.copyOf(participatingAgents);
        Objects.requireNonNull(firstObserved);
        Objects.requireNonNull(lastObserved);
        Objects.requireNonNull(strength);
    }
}
```

#### NormStrength (enum)

```java
public enum NormStrength {
    EMERGING,      // observed but not yet consistent
    ESTABLISHED,   // consistent adherence across agents
    DECLINING      // adherence dropping — norm may be fading
}
```

#### NormObservation (record)

A single observed interaction pattern stored in CBR for norm detection.

```java
public record NormObservation(
        String observationId,
        String tenantId,
        String behavioralPattern,       // what was observed
        Set<String> involvedAgents,
        String conversationId,
        Instant observedAt,
        boolean patternFollowed         // did agents follow the pattern?
) {
    public NormObservation {
        Objects.requireNonNull(observationId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(behavioralPattern);
        involvedAgents = Set.copyOf(involvedAgents);
        Objects.requireNonNull(observedAt);
    }
}
```

#### DetectedNorms (record)

Output of SocialNormDetector.tick().

```java
public record DetectedNorms(
        List<SocialNorm> norms,
        int observationsAnalysed,
        Instant analysedAt
) {
    public DetectedNorms {
        norms = List.copyOf(norms);
        Objects.requireNonNull(analysedAt);
    }

    public List<SocialNorm> established() {
        return norms.stream()
                .filter(n -> n.strength() == NormStrength.ESTABLISHED)
                .toList();
    }
}
```

#### NormDetectionTick (sealed interface)

```java
public sealed interface NormDetectionTick {
    record NoChange(@Nullable String reason) implements NormDetectionTick {}
    record Updated(DetectedNorms previous, DetectedNorms current,
                   List<String> newNormIds,
                   List<String> declinedNormIds) implements NormDetectionTick {}
}
```

#### SocialNormDetector

Compositor — reads norm observations from CBR store, detects patterns via
frequency and consistency analysis.

```java
@ApplicationScoped
public class SocialNormDetector {

    private final CbrCaseMemoryStore store;
    private final MemoryDomain domain;
    private final NormDetectionConfig config;
    private final ConcurrentHashMap<String, DetectedNorms> cache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks =
            new ConcurrentHashMap<>();

    // Constructor: store, domain, config

    public NormDetectionTick tick(String tenantId) {
        // 1. Query CBR store for norm observation cases in tenant
        // 2. Group by behavioralPattern
        // 3. Compute adherenceRate = followed / total per pattern
        // 4. Classify strength: EMERGING (< threshold), ESTABLISHED (>= threshold),
        //    DECLINING (was ESTABLISHED, now dropping)
        // 5. Compare with cached state, return Updated or NoChange
    }

    public Optional<DetectedNorms> currentNorms(String tenantId) {
        return Optional.ofNullable(cache.get(tenantId));
    }
}
```

#### NormDetectionConfig (record)

```java
public record NormDetectionConfig(
        int minObservationsForNorm,     // minimum observations to detect
        double establishedThreshold,    // adherence rate to be ESTABLISHED
        double decliningThreshold,      // adherence rate to be DECLINING
        int minAgentsForNorm            // minimum participating agents
) {
    public static NormDetectionConfig defaults() {
        return new NormDetectionConfig(10, 0.7, 0.4, 2);
    }
}
```

#### DriveAlignment (record)

Measures alignment between two or more agents' drive profiles.

```java
public record DriveAlignment(
        Set<String> agentIds,
        Map<DriveAxis, Double> alignmentPerAxis,  // [0, 1] per axis
        double compositeAlignment,                 // weighted average
        @Nullable DriveAxis dominantSharedAxis,   // highest aligned axis
        Instant computedAt
) {
    public DriveAlignment {
        agentIds = Set.copyOf(agentIds);
        alignmentPerAxis = Map.copyOf(alignmentPerAxis);
        if (compositeAlignment < 0.0 || compositeAlignment > 1.0)
            throw new IllegalArgumentException("compositeAlignment must be in [0, 1]");
        Objects.requireNonNull(computedAt);
    }
}
```

#### CollectiveGoalProposal (record)

Proposed joint goal from drive alignment detection.

```java
public record CollectiveGoalProposal(
        DriveAlignment alignment,
        String goalDescription,
        Set<String> proposedParticipants,
        DriveAxis primaryAxis
) {
    public CollectiveGoalProposal {
        Objects.requireNonNull(alignment);
        Objects.requireNonNull(goalDescription);
        proposedParticipants = Set.copyOf(proposedParticipants);
        Objects.requireNonNull(primaryAxis);
    }
}
```

#### CollectiveGoalTick (sealed interface)

```java
public sealed interface CollectiveGoalTick {
    record NoChange(@Nullable String reason) implements CollectiveGoalTick {}
    record Proposed(List<CollectiveGoalProposal> proposals,
                    List<DriveAlignment> alignments) implements CollectiveGoalTick {}
}
```

#### CollectiveGoalFormation

Compositor — reads multiple agents' DriveProfiles, detects alignment, proposes
JointIntentions when alignment exceeds threshold.

```java
@ApplicationScoped
public class CollectiveGoalFormation {

    private final DriveOrchestrator driveOrchestrator;
    private final List<String> agentIds;       // provided by consumer (D12)
    private final CollectiveGoalConfig config;
    private final ConcurrentHashMap<String, List<CollectiveGoalProposal>> cache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks =
            new ConcurrentHashMap<>();

    // Constructor: driveOrchestrator, agentIds, config

    public CollectiveGoalTick tick(String tenantId) {
        // 1. Read DriveProfile for each agent in agentIds
        // 2. Compute pairwise DriveAlignment
        // 3. Filter by compositeAlignment >= threshold
        // 4. Propose collective goals for aligned groups
        // 5. Return Proposed or NoChange
    }

    public Optional<List<CollectiveGoalProposal>> currentProposals(String tenantId) {
        return Optional.ofNullable(cache.get(tenantId));
    }
}
```

#### CollectiveGoalConfig (record)

```java
public record CollectiveGoalConfig(
        double alignmentThreshold,     // minimum composite alignment to propose
        int minAlignedAgents,          // minimum agents in alignment group
        Duration cooldown              // between proposals for same group
) {
    public static CollectiveGoalConfig defaults() {
        return new CollectiveGoalConfig(0.6, 2, Duration.ofMinutes(60));
    }
}
```

### Layer 2 Deferrals (D11)

#### Cross-axis goal composition

DerivedTheme carries `Map<DriveAxis, Double> axisModulationWeights`. When multiple
weights are significant (e.g., CURIOSITY: 0.4, AFFILIATION: 0.5), the theme is
cross-axis. GoalProposalOrchestrator (or a future CrossAxisGoalMapper) can detect
cross-axis themes and propose compound goals: "learn about X by engaging with Y"
(curiosity + affiliation).

The foundation type support is already in DerivedTheme. The mapper implementation
is deferred to a child issue.

#### Governed priority escalation

A new SPI in engine-api:

```java
public interface GoalPriorityEscalationPolicy {
    GoalPriority evaluate(AgentGoal goal, GoalEscalationContext context);
}

public record GoalEscalationContext(
        @Nullable NarrativeState narrative,
        @Nullable DriveProfile drives
) {}
```

GoalFormationService checks the policy before registration. When a drive-sourced
goal aligns with a core identity theme (DerivedTheme.salience > threshold), the
policy can escalate from SECONDARY to PRIMARY with provenance:
`attributes = {"escalatedBy": "narrative", "theme": theme.label()}`.

The SPI lives in engine-api. The NarrativeGoalEscalationPolicy implementation
lives in blocks. Implementation deferred to #145 (NarrativeFeedback).

## Package Structure

```
blocks/src/main/java/io/casehub/blocks/agentic/social/narrative/
├── NarrativeFragment.java          (sealed interface)
├── IndividualEpisode.java          (record)
├── GroupEpisode.java               (record)
├── DerivedTheme.java               (record)
├── NarrativeScope.java             (enum: INDIVIDUAL, GROUP)
├── NarrativeState.java             (record — scopeId, scope, fragments)
├── NarrativeConfig.java            (record)
├── NarrativeSynthesisGate.java     (record)
├── NarrativeTick.java              (sealed interface)
├── NarrativeSynthesisTick.java     (sealed interface)
├── NarrativeStore.java             (SPI)
├── NoOpNarrativeStore.java         (@DefaultBean)
├── NarrativeModulation.java        (utility — NarrativeState → Map<DriveAxis, Double>)
├── NarrativeSynthesiser.java       (effectful — LLM synthesis)
└── NarrativeOrchestrator.java      (compositor — tick + accessor)

blocks/src/main/java/io/casehub/blocks/memory/
├── ReflectionQueryStore.java       (NEW SPI — alongside ReflectionStore)
└── NoOpReflectionQueryStore.java   (NEW @DefaultBean)

blocks/src/main/java/io/casehub/blocks/agentic/social/emergence/
├── SocialNorm.java                 (record)
├── NormStrength.java               (enum)
├── NormObservation.java            (record)
├── DetectedNorms.java              (record)
├── NormDetectionTick.java          (sealed interface)
├── NormDetectionConfig.java        (record)
├── SocialNormDetector.java         (compositor)
├── DriveAlignment.java             (record)
├── CollectiveGoalProposal.java     (record)
├── CollectiveGoalTick.java         (sealed interface)
├── CollectiveGoalConfig.java       (record)
└── CollectiveGoalFormation.java    (compositor)

engine/api/src/main/java/io/casehub/api/spi/routing/
├── GoalPriorityEscalationPolicy.java  (NEW SPI)
└── GoalEscalationContext.java         (NEW record)
```

Sub-packages under `social` — narrative identity and social emergence are part
of the social cognition module, not separate concerns.

## Upstream Changes Required

| Repo | Change | Why |
|------|--------|-----|
| engine-api | `GoalPriorityEscalationPolicy` SPI + `GoalEscalationContext` record | Governed priority escalation (D11) |
| blocks | `ReflectionQueryStore` SPI + `NoOpReflectionQueryStore` @DefaultBean (in memory package) | NarrativeSynthesiser reads stored reflections |
| blocks | Add `@Nullable Map<DriveAxis, Double>` parameter to `DriveComposer.compose()` | Third modulation layer (D4) — pre-computed coefficients, no narrative type dependency |
| blocks | Add `Instance<NarrativeOrchestrator>` to `DriveOrchestrator` constructor | Optional narrative modulation source |
| blocks | Add `narrativeSection()` to `CognitiveObservationSections` | Prompt rendering for narrative identity |

All changes are additive. The DriveComposer.compose() parameter addition is the
only source-breaking change — existing callers must add `null` as the new argument.

### Assumptions

- **Co-location**: CollectiveGoalFormation assumes all agents whose DriveProfiles
  are read run in the same JVM and are ticked by the same scheduler. Distributed
  agents need a DriveStore persistence layer (noted in Layer 1 follow-ups) for
  cross-JVM drive profile access.
- **Norm pattern normalisation**: NormObservation.behavioralPattern values must
  be normalised before storage — otherwise string-equality grouping in
  SocialNormDetector fragments norms with paraphrased descriptions. The
  normalisation strategy is designed in #146.

## Testing Strategy

### Foundation type tests (plain JUnit 5)

- NarrativeFragment sealed hierarchy: verify IndividualEpisode, GroupEpisode,
  DerivedTheme construction, validation, defensive copies
- NarrativeState: verify typed accessors (episodes(), themes(), dominantTheme()),
  empty state handling, immutability
- DriveAlignment: verify computation from DriveProfile pairs, alignment per axis,
  composite calculation
- SocialNorm: verify strength transitions, adherenceRate validation
- NormObservation: verify construction, immutability

### NarrativeOrchestrator tests (Mockito)

- tick() reads from NarrativeStore, caches state
- currentNarrative() returns cached state
- NoChange when store returns same state as cached
- Updated when store returns different state
- Per-agent isolation via state key
- Thread safety — concurrent tick calls for different agents

### NarrativeSynthesiser tests (Mockito)

- Composite gate: skips when count below threshold
- Composite gate: skips when novelty below threshold despite count
- Composite gate: bypasses on quiet period
- Calls AgentProvider with assembled prompt
- Writes NarrativeState to NarrativeStore
- Returns Synthesised with correct reflection count

### DriveComposer modulation tests

- Null modulation map = no modulation (backward compatible)
- Single-axis modulation amplifies correct drive
- Multi-axis modulation applies all coefficients
- Negative coefficients dampen drives
- Modulated intensity clamped to [min, max]
- NarrativeModulation utility: converts NarrativeState → Map correctly

### SocialNormDetector tests (Mockito)

- Detects EMERGING norm from repeated pattern
- Promotes to ESTABLISHED when adherenceRate crosses threshold
- Marks DECLINING when adherence drops
- Requires minimum observation count
- Requires minimum agent participation
- Per-tenant isolation

### CollectiveGoalFormation tests (Mockito)

- Detects alignment between two agents with similar drive profiles
- No proposals when alignment below threshold
- Proposes JointIntention for aligned groups
- Cooldown prevents repeated proposals
- Handles agents with no DriveProfile (Optional.empty())

### CognitiveObservationSections tests

- narrativeSection() renders dominant theme, significant episodes, secondary themes
- Empty state produces "No established identity yet."

## What This Does NOT Include

- **NarrativeOrchestrator implementation** — child #143 implements the compositor
- **NarrativeStore persistence** — child #144 implements CbrNarrativeStore
- **NarrativeFeedback implementation** — child #145 implements the DriveComposer
  modulation and GoalPriorityEscalationPolicy
- **SocialNormDetector implementation** — child #146 implements detection logic
- **CollectiveGoalFormation implementation** — child #147 implements alignment
  detection and JointIntention bridging
- **GroupNarrativeOrchestrator** — child #148 implements group-scoped narrative
- **NormObservationRecorder** — the observation write path (ConversationListener
  or MessageObserver that records interaction patterns to CBR) is designed in #146
- **CrossAxisGoalMapper** — compound goal formation from cross-axis themes;
  foundation types support it (DerivedTheme.axisModulationWeights) but the mapper
  is a follow-up

## References

- [ADR-0001-drive-compositor-pattern.md] — compositor pattern (tick without record)
- [docs/blog/2026-08-21-mdp04-what-does-the-agent-want.md] — three-layer vision
- [docs/specs/issue-129-drive-architecture/2026-08-21-drive-architecture-design.md] — Layer 1 spec
- [docs/specs/issue-136-autonomous-goal-generation/2026-08-23-autonomous-goal-generation-design.md] — Layer 2 spec
- [InnerLifeOrchestrator.java] — compositor precedent, ContentQualityGate pattern
- [DriveOrchestrator.java] — compositor implementation
- [DriveComposer.java] — modulation algebra (mood, personality)
- [MemoryHygieneOrchestrator.java] — tick + store pattern, ReflectionStore write path
- [ReflectionStore.java] — write-only SPI (R1-02 finding)
- [CognitiveObservationSections.java] — agent prompt rendering
- [JointIntention.java] — commitment lifecycle (form/activate/reconsider/drop/fulfill)
- [CoalitionProposal.java] — capability-based team assembly
- [CommonGroundAnalyser.java] — epistemic status tracking
- [TokenJaccardDistance.java] — novelty scoring (reused for synthesis gate)
- [GE-20260823-11ffe5] — CDI functional interface self-filtering gotcha
- [GE-20260810-8bc960] — ConversationOrchestrator closed loop
- [GE-20260816-6635e1] — ChannelObserver composition technique
- [GE-20260820-c19b68] — CbrQuery lacks producerAgentId filter
- [GE-20260820-aa31ab] — Memory retention score composite masking
- [GE-20260811-e941cc] — AgentDisposition vs DispositionProfile type split
- [Park et al., 2023] — Generative Agents — emergent social behavior
- [Takata et al., 2024] — spontaneous individuality in LLM agents
- [McAdams, 2001] — Narrative Identity Theory
- [Boyd & Richerson] — Cultural Evolution Theory
- [Deci & Ryan, 2000] — Self-Determination Theory
