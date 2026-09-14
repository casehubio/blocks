# CognitiveObservationSections — Beliefs, Principles, Trust, Norms Renderers

**Issue:** #260
**Date:** 2026-09-14

## Summary

Add four factory methods to `CognitiveObservationSections` for rendering cognitive profile content (beliefs, principles, trust, norms) into `ObservationSection` format. Follows the established pattern exactly — static methods accepting domain types, returning `ObservationSection.ItemList`.

## New Types

Two new records and one enum, all in `io.casehub.blocks.summarisation.observation.affordance`:

### `TrustLevel` enum

```java
public enum TrustLevel { HIGH, MODERATE, LOW, UNKNOWN }
```

### `TrustSummary` record

```java
public record TrustSummary(
    String subjectName,
    TrustLevel level,
    @Nullable String reason) {
    public TrustSummary {
        Objects.requireNonNull(subjectName);
        Objects.requireNonNull(level);
    }
}
```

### `Principle` record

```java
public record Principle(
    String text,
    @Nullable String category) {
    public Principle {
        Objects.requireNonNull(text);
    }
}
```

## New Factory Methods

All methods on `CognitiveObservationSections`:

### `beliefsSection(List<Belief<?>>, Set<String> revisedKeys)`

- Header: `"Your Beliefs"`
- Empty message: `"No established beliefs."`
- Each belief renders as: `key: value` (calling `toString()` on the generic value)
- Beliefs whose key is in `revisedKeys` render with a `[REVISED]` prefix: `[REVISED] key: value`
- Sort by entrenchment descending (most entrenched first), then by key alphabetically

### `principlesSection(List<Principle>)`

- Header: `"Your Principles"`
- Empty message: `"No guiding principles."`
- When category is present: `[Category] text`
- When category is null: plain `text`
- No sorting — render in input order (consumer controls presentation order)

### `trustSection(List<TrustSummary>)`

- Header: `"Your Trust"`
- Empty message: `"No trust assessments."`
- Each renders as: `SubjectName: LEVEL` (e.g., `Penelope: HIGH`)
- When reason is present, append: `SubjectName: LEVEL — reason`
- Sort by level ordinal (HIGH first, UNKNOWN last), then by subjectName alphabetically

### `normsSection(List<SocialNorm>)`

- Header: `"Your Active Norms"`
- Empty message: `"No active norms."`
- Each renders as: `[STRENGTH] description` (e.g., `[ESTABLISHED] Take turns speaking`)
- Sort by strength ordinal (ESTABLISHED first, DECLINING last), then by description alphabetically

## Consumer Mapping

The consumer (e.g., `SocialAvatarCognition`) maps from `MindMapNode` query results to these blocks types. Example mapping for beliefs:

```java
var beliefNodes = mindmap.query("belief", agentId);
var beliefs = beliefNodes.stream()
    .map(n -> Belief.of(n.name(), n.property("content").orElse(""), ...))
    .toList();
var revisedKeys = beliefNodes.stream()
    .filter(n -> n.updatedAt().isAfter(threshold))
    .map(MindMapNode::name)
    .collect(toSet());
var section = CognitiveObservationSections.beliefsSection(beliefs, revisedKeys);
```

This is the same pattern used by existing sections — `AgentGoal`, `Memory`, `DriveProfile` are all mapped from domain sources at the call site.

## Testing

Follow the existing test pattern in `CognitiveObservationSectionsTest`:

- Each method gets tests for: populated input, empty input (empty message), edge cases
- `beliefsSection`: sorted by entrenchment, revised markers, mixed revised/non-revised, empty
- `principlesSection`: with category, without category, empty
- `trustSection`: sorted by level, with/without reason, empty
- `normsSection`: sorted by strength, empty

## References

- `CognitiveObservationSections.java` — established factory method pattern
- `CognitiveObservationSectionsTest.java` — test pattern
- `ObservationSection.java` — sealed interface (ItemList, TextBlock, EntityGroup)
- `Belief.java` / `BeliefSet.java` — AGM belief revision types
- `SocialNorm.java` / `NormStrength.java` — social emergence types
- `MindMapNode.java` — neocortex mindmap query result (consumer maps from this)
- Issue casehubio/examples#52 — wacky-manor social cognition integration (consumer)
