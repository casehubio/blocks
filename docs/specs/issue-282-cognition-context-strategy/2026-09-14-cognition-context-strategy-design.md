# CognitionContextStrategy — Situational Context Filtering for CognitionCore

**Issue:** casehubio/blocks#282
**Date:** 2026-09-14
**Scope:** 3 new SPIs + 1 record type, CognitionCore method signature changes, SocialAvatarCognition adapter updates

## Problem

CognitionCore composes cognitive orchestrators generically but has no mechanism for situational context. Any situated agent (game character, NPC, virtual colleague) needs:
- **Subject scoping** — who/what is contextually relevant right now
- **Interaction mapping** — domain events (STEAL, GIVE, MOVE) mapped to cognitive signals with appropriate valence and magnitude
- **Norm filtering** — social norms scoped to the current situation (proximity, relationship state)

Without these SPIs, every CognitionCore consumer builds its own adapter layer. The first consumer is wacky-manor (casehubio/examples#54), which needs room-aware norm filtering, action-type trust mapping, and proximity-scoped queries.

## Solution

Three separate SPIs with distinct integration points, not one unified interface. Each SPI is independently useful and integrates at a different architectural layer.

### SPI 1: SubjectResolver

```java
@FunctionalInterface
public interface SubjectResolver {
    Set<String> relevantSubjects(String agentId, String tenantId);
}
```

**Package:** `io.casehub.blocks.agentic.social`
**Integration:** Per-call parameter to `CognitionCore.tick()`. Replaces `Set<String> activeSubjects`.

### SPI 2: InteractionMapper

```java
@FunctionalInterface
public interface InteractionMapper {
    CognitiveImpact mapInteraction(String agentId, String targetId,
                                    String interactionType);
}
```

**Package:** `io.casehub.blocks.agentic.social`
**Integration:** Adapter-layer SPI consumed by `SocialAvatarCognition`. Transforms domain interactions into `CognitiveImpact`, passed as a per-call parameter to `CognitionCore.recordInteraction()`. CognitionCore never sees `InteractionMapper` directly.

### SPI 3: NormFilter

```java
@FunctionalInterface
public interface NormFilter {
    List<SocialNorm> filter(List<SocialNorm> norms, String agentId, String tenantId);
}
```

**Package:** `io.casehub.blocks.agentic.social.emergence` (alongside `SocialNorm`, `SocialNormDetector`)
**Integration:** Applied at the prompt assembly layer before `CognitiveObservationSections.normsSection()`. Independent of CognitionCore — norms are consumed at prompt rendering, not cognitive processing.

### CognitiveImpact record

```java
public record CognitiveImpact(
    @Nullable InteractionSignal userModelSignal,
    @Nullable MoodSignal moodSignal,
    boolean suppressBdiExtraction,
    @Nullable EngagementSignal strategySignal
) {
    public static CognitiveImpact fromText(String description) {
        return new CognitiveImpact(
            new InteractionSignal.CustomSignal(description, QualitySignal.NEUTRAL),
            null, false, null);
    }
}
```

**Override semantics:** `null` = CognitionCore runs its default processing for that channel (mood LLM appraisal, BDI extraction, etc.). Non-null = replace default processing with the provided signal. `MoodSignal.DirectShift` is the existing variant for bypassing LLM appraisal. `EngagementSignal` provides full fidelity for CBR case construction in `StrategyLearningOrchestrator`.

## CognitionCore method changes

### tick()

```java
// Before:
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 Set<String> activeSubjects)

// After:
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 SubjectResolver resolver)
```

`SubjectResolver` is the sole authority for subject determination. No ambiguity between resolver and caller-provided set. SocialAvatarCognition wraps caller-provided `Set<String> activeSubjects` into a lambda: `(aid, tid) -> activeSubjects`.

### recordInteraction()

```java
// Before:
public void recordInteraction(String agentId, String tenantId,
                               @Nullable String subjectId,
                               String userMessage, String response)

// After:
public void recordInteraction(String agentId, String tenantId,
                               @Nullable String subjectId,
                               String userMessage, String response,
                               @Nullable CognitiveImpact impact)
```

When `impact` is null: all-default processing (current behaviour). When non-null:
- `impact.userModelSignal()` non-null → record it instead of hardcoded `CustomSignal`
- `impact.moodSignal()` non-null → record it, skip LLM appraisal
- `impact.suppressBdiExtraction()` true → skip LLM BDI extraction
- `impact.strategySignal()` non-null → record it instead of hardcoded `EngagementSignal` construction

## AvatarCognition published interface

`AvatarCognition` (in `casehub-blocks-speech-api`) does NOT change. `SocialAvatarCognition` adapts internally:

- Receives `Set<String> activeSubjects` from `AvatarCognition.tick()` → wraps into `SubjectResolver` lambda
- Receives `(userMessage, response)` from `AvatarCognition.recordInteraction()` → optionally maps via injected `InteractionMapper` to produce `@Nullable CognitiveImpact`
- If a custom `SubjectResolver` is configured as a CDI bean, it takes precedence over the caller-provided set

Game integrations configure `SubjectResolver`, `InteractionMapper`, and `NormFilter` as CDI beans injected into `SocialAvatarCognition` — not through `AvatarCognition`. Cognitive customization belongs at the cognition layer, not the speech layer.

## NormFilter integration

`NormFilter` is applied at prompt assembly time, not inside CognitionCore. The integration point is wherever norms are rendered into the agent's prompt — currently `CognitiveObservationSections.normsSection(List<SocialNorm>)`.

The caller (e.g., `SocialAvatarCognition` or a custom prompt assembler) applies the filter before rendering:

```java
var norms = normDetector.currentNorms(tenantId).norms();
if (normFilter != null) {
    norms = normFilter.filter(norms, agentId, tenantId);
}
var section = CognitiveObservationSections.normsSection(norms);
```

## DefaultBean producers

All three SPIs get `@DefaultBean` producers (per blocks#280 pattern):
- `SubjectResolver` → returns `Set.of()` (no subjects — consumers must provide their own or pass explicitly)
- `InteractionMapper` → returns `CognitiveImpact.fromText(interactionType)` (passthrough)
- `NormFilter` → returns input unchanged (no filtering)

These live in `SocialCognitionDefaultBeans` (extending the class created in #280).

## Testing

Plain JUnit 5 (no Quarkus runtime, per project convention):

- `SubjectResolverTest` — lambda implementations return expected subjects
- `InteractionMapperTest` — domain events map to expected `CognitiveImpact` fields
- `NormFilterTest` — filtering by various criteria (agent presence, context attributes)
- `CognitiveImpactTest` — override semantics: null = default, non-null = override; `fromText()` factory
- `CognitionCoreTest` — updated to verify:
  - `tick()` calls `resolver.relevantSubjects()` and iterates returned subjects
  - `recordInteraction()` with non-null `impact` uses provided signals instead of defaults
  - `recordInteraction()` with null `impact` preserves current behaviour
  - `suppressBdiExtraction` skips LLM BDI call
  - Non-null `moodSignal` skips LLM appraisal

## Migration

One-file migration: `SocialAvatarCognition.java`. Changes:
1. `tick()` calls pass `(aid, tid) -> activeSubjects` lambda instead of raw set
2. `recordInteraction()` calls pass `null` as impact (preserving current behaviour) unless `InteractionMapper` is injected
3. Optional CDI injection of `SubjectResolver`, `InteractionMapper`, `NormFilter` via `Instance<T>` (unsatisfied = use defaults)

No other callers of CognitionCore exist in the codebase.

## References

- CognitionCore.java:94-133 (tick method with activeSubjects parameter)
- CognitionCore.java:135-169 (recordInteraction with 4 hardcoded orchestrator channels)
- SocialAvatarCognition.java (sole production caller of both methods)
- SocialNormDetector.java (norm detection — no CognitionCore reference)
- CognitiveObservationSections.java (normsSection — prompt assembly integration point for NormFilter)
- MoodSignal.java (DirectShift variant for external mood override)
- EngagementSignal.java (TurnOutcome structure for strategy channel)
- InteractionSignal.java (sealed interface — CustomSignal variant)
- AvatarCognition.java (published SPI in speech-api — interface unchanged)
- casehubio/examples#54 (wacky-manor — first consumer)
- casehubio/blocks#280 (DefaultBean pattern precedent)
