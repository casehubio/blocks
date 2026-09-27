# CognitiveAttentionMediator — Bridge Neocortex Attention Signals to CognitionCore

**Issue:** casehubio/blocks#304
**Date:** 2026-09-27
**Status:** Design complete

## 1. Context

casehubio/neocortex#381 landed the progressive cognitive attention model — signal types, `CognitiveAttentionAccumulator` with adaptive threshold, 6 consolidation phases emitting signals, real-time event observers, and the `CognitiveAttentionRequired` CDI event. The neocortex side is complete.

Nothing in blocks observes the event yet. The agent's cognitive loop (CognitionCore) has no awareness of attention signals. This issue bridges the gap with three components: a CDI mediator, CognitionCore integration, and an attention prompt section.

## 2. Architecture

### 2.1 Data Flow

```
Neocortex consolidation phases
  → emit AttentionSignal (via ConsolidationPhase.signals())
  → CognitiveAttentionAccumulator threshold check
  → fires CognitiveAttentionRequired CDI event
  → CognitiveAttentionMediator @Observes, queues per-principal
  → CognitionCore.tick() drains queue at FOUNDATION phase start
  → CognitionCore.promptSections() renders AttentionPromptSection
                                    + overrides gated sections via AttentionRelevance
```

### 2.2 Module Placement

All new classes live in `blocks-core` (`io.casehub.blocks.agentic.social` package), alongside CognitionCore. CDI wiring in `blocks` module (`BlocksBeans`).

The types consumed — `AttentionBriefing`, `AttentionSignal`, `CognitiveAttentionRequired`, `SignalCategory` — all live in neocortex's `mindmap-api`, which blocks already depends on.

## 3. Components

### 3.1 CognitiveAttentionMediator

`@ApplicationScoped` CDI bean in blocks-core. Bridges CDI events to CognitionCore instances (which are POJOs, not CDI-managed).

```java
@ApplicationScoped
public class CognitiveAttentionMediator {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<AttentionBriefing>>
        attentionQueues = new ConcurrentHashMap<>();

    void onAttentionRequired(@Observes CognitiveAttentionRequired event) {
        attentionQueues
            .computeIfAbsent(event.briefing().principalId(),
                k -> new ConcurrentLinkedQueue<>())
            .add(event.briefing());
    }

    public Optional<AttentionBriefing> drainAttention(String principalId) {
        var queue = attentionQueues.get(principalId);
        if (queue == null || queue.isEmpty()) return Optional.empty();
        List<AttentionBriefing> drained = new ArrayList<>();
        AttentionBriefing b;
        while ((b = queue.poll()) != null) drained.add(b);
        if (drained.isEmpty()) return Optional.empty();
        return Optional.of(merge(drained));
    }
}
```

**Merge semantics:** When multiple briefings are drained, merge produces a single briefing with:
- Latest `urgencyP75` (most recent observation)
- Union of all signals, deduplicated by `sourceNodeId + category`, sorted by significance descending
- Latest `generatedAt`
- `principalId` and `tenantId` from the first briefing (all should match)

### 3.2 AttentionRelevance

Package-private static utility. Maps each cognitive domain to the signal categories that make it relevant. Used by CognitionCore to determine whether a disabled section should be force-included when attention signals are present.

```java
final class AttentionRelevance {

    static final Set<SignalCategory> GOALS = EnumSet.of(
        URGENCY_SPIKE, GOAL_RECOGNIZED, DECAY_DETECTED,
        BLOCKER_RESOLVED, PRIORITY_SHIFT);

    static final Set<SignalCategory> DRIVES = EnumSet.of(DRIVE_SHIFT);

    static final Set<SignalCategory> MOOD = EnumSet.of(AFFECT_CHANGE);

    static final Set<SignalCategory> MENTAL_MODEL = EnumSet.of(
        RELATIONSHIP_STAGE, BELIEF_REVISED);

    static final Set<SignalCategory> USER_MODEL = EnumSet.of(RELATIONSHIP_STAGE);

    static boolean overrides(AttentionBriefing briefing,
                             Set<SignalCategory> relevance) {
        return briefing.signals().stream()
            .anyMatch(s -> relevance.contains(s.category()));
    }

    private AttentionRelevance() {}
}
```

**Unmapped categories:** `MERGE_CANDIDATE` and `EXPERIENCE_GRADUATED` have no corresponding prompt section — they are informational signals in the briefing but don't require additional cognitive context to be rendered.

**Design rationale (D3):** The reverse mapping direction (domain → categories, not category → domain) is more natural ("which signals are relevant to goals?"), fully type-safe (enum values only, no string subsystem names), and efficient (`EnumSet.contains()` is O(1) bitwise).

### 3.3 CognitionConfig Changes

Add `attentionEnabled` as the 13th boolean field.

Update:
- `all()` — returns `true` for attentionEnabled
- `none()` — returns `false`
- `with(String, boolean)` — add `"attention"` case
- `withDirectives()` — preserve attentionEnabled

### 3.4 CognitionCore Changes

**New fields:**

```java
private final @Nullable CognitiveAttentionMediator attentionMediator;
private volatile @Nullable AttentionBriefing lastBriefing;
```

**5th constructor overload:**

Adds `@Nullable CognitiveAttentionMediator attentionMediator` to the canonical (13-param, now 14-param) constructor. The 4 existing convenience constructors pass `null` for the mediator — no existing callers change.

**tick() — FOUNDATION phase (drain attention):**

Always drain when mediator is present (prevents unbounded queue growth). Only set `lastBriefing` when `attentionEnabled` is true (D2 refinement).

```java
// At the start of FOUNDATION, before mood tick:
this.lastBriefing = null;  // clear stale briefing from prior tick
if (attentionMediator != null) {
    var briefing = attentionMediator.drainAttention(agentId);
    if (config.attentionEnabled()) {
        this.lastBriefing = briefing.orElse(null);
    }
}
```

**promptSections() — isEnabled helper + AttentionPromptSection:**

```java
private boolean isEnabled(boolean configFlag, Set<SignalCategory> relevance) {
    return configFlag || (lastBriefing != null && config.attentionEnabled()
        && AttentionRelevance.overrides(lastBriefing, relevance));
}
```

Replace all config-gated section additions with `isEnabled()`:

```java
if (isEnabled(config.goalsEnabled(), AttentionRelevance.GOALS) && goals != null) {
    sections.add(new GoalPromptSection(goals));
}
```

When `lastBriefing` is present and `attentionEnabled`, add `AttentionPromptSection`:

```java
if (config.attentionEnabled() && lastBriefing != null) {
    sections.add(new AttentionPromptSection(lastBriefing));
}
```

**Accessor:**

```java
public @Nullable AttentionBriefing lastBriefing() { return lastBriefing; }
```

### 3.5 AttentionPromptSection

New `PromptSection` implementation in blocks-core. Takes `AttentionBriefing`, renders top-N signals.

```java
public class AttentionPromptSection implements PromptSection {

    private final AttentionBriefing briefing;

    public AttentionPromptSection(AttentionBriefing briefing) {
        this.briefing = briefing;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var signals = briefing.signals();
        if (signals.isEmpty()) return null;
        var sb = new StringBuilder("Attention required:");
        for (var signal : signals) {
            sb.append("\n- [").append(signal.category().name()).append("] ")
              .append(signal.sourceName());
            if (signal.significance() > 0) {
                sb.append(" (").append(String.format("%.2f", signal.significance())).append(")");
            }
            sb.append(": ").append(signal.reason());
        }
        return sb.toString();
    }
}
```

### 3.6 CDI Wiring

**BlocksBeans** — produce `CognitiveAttentionMediator` and inject into `SocialAvatarCognition`:

```java
// BlocksBeans already has Instance<...> fields for optional deps.
// Add:
@jakarta.inject.Inject Instance<CognitiveAttentionMediator> attentionMediatorInstance;

@Produces @ApplicationScoped
public SocialAvatarCognition socialAvatarCognition(
        MoodOrchestrator mood, DriveOrchestrator drives,
        MentalModelOrchestrator mentalModel,
        UserModelOrchestrator userModel,
        StrategyLearningOrchestrator strategy) {
    return new SocialAvatarCognition(
            mood, drives, mentalModel, userModel, strategy,
            optionalFrom(narrativeOrchestratorInstance),
            optionalFrom(goalProposalOrchestratorInstance),
            optionalFrom(innerLifeOrchestratorInstance),
            optionalFrom(agentRegistryInstance),
            optionalFrom(attentionMediatorInstance));  // new
}
```

**SocialAvatarCognition** — accept optional mediator, pass to CognitionCore:

```java
public SocialAvatarCognition(...,
                              Optional<CognitiveAttentionMediator> attentionMediator) {
    // ...
    this.core = new CognitionCore(mood, drives, userModel, mentalModel, strategy,
            narrative.orElse(null), goals.orElse(null), null,
            innerLife.orElse(null), null, CognitionConfig.all(),
            null, null,
            attentionMediator.orElse(null));  // new param
}
```

## 4. Testing Strategy

| Test | Scope | Module |
|------|-------|--------|
| CognitiveAttentionMediatorTest | Queue per-principal isolation, drain merge semantics, empty drain, concurrent access | blocks-core (unit) |
| AttentionRelevanceTest | Each constant maps correct categories, overrides() returns true/false correctly | blocks-core (unit) |
| AttentionPromptSectionTest | Renders signals in expected format, empty signals returns null | blocks-core (unit) |
| CognitionCoreTest (extended) | tick() drains mediator at FOUNDATION, lastBriefing set when enabled, drain-and-discard when disabled, isEnabled() overrides gated sections | blocks (unit) |
| CognitionConfigTest (extended) | attentionEnabled in all()/none()/with()/without() | blocks (unit) |

## 5. Not in Scope

| Issue | What | When |
|-------|------|------|
| #305 | TopN expansion for attention-aware sections | When first section adds topN truncation |
| #306 | Runtime-customizable attention override policy | Follow-up: extract AttentionRelevance to SPI |
| #297 | Spring coverage: BlocksAutoConfiguration mediator wiring | Part of Spring expansion audit |

## References

- neocortex spec §7: `/Users/mdproctor/claude/casehub/slots/203/neocortex/docs/specs/issue-381-progressive-attention-model/2026-09-25-progressive-attention-model-design.md` (lines 474-584)
- CognitionCore.java: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionCore.java`
- CognitionConfig.java: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/CognitionConfig.java`
- SocialAvatarCognition.java: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/prompt/SocialAvatarCognition.java`
- BlocksBeans.java: `blocks/src/main/java/io/casehub/blocks/BlocksBeans.java` (lines 304-316)
- AttentionBriefing.java: `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/AttentionBriefing.java`
- SignalCategory.java: `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/SignalCategory.java`
