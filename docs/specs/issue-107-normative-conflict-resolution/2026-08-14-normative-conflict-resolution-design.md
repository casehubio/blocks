# Normative Conflict Resolution — Design Spec

**Issue:** casehubio/blocks#107
**Branch:** issue-105-pattern-gaps
**Date:** 2026-08-14

## Summary

A generic `ConflictResolutionStrategy<T>` SPI in `io.casehub.blocks.normative` for resolving conflicts between competing norm decisions. Four resolution strategies (priority, specificity, recency, most-restrictive) plus an escalation strategy. Speculative — no consumer yet; engine integration requires a separate change to `ChainedActionRiskClassifier` in engine-api.

## Architecture

### Package: `io.casehub.blocks.normative`

New top-level package in blocks. Generic — not coupled to oversight or any specific decision type. The primary expected consumer is the oversight pipeline (composing `NormDecision<RiskDecision>`), but the SPI works for any decision type where multiple sources can produce conflicting outcomes.

### Dependency direction

blocks depends on engine-api (downstream). engine-api cannot depend on blocks. The SPI lives in blocks; engine integration requires either:
- Engine-api exposing a strategy injection point on `ChainedActionRiskClassifier`
- A blocks-level adapter that consumers use instead of the engine chain

Neither is in scope for #107. The SPI is self-contained and callable directly.

## Types

### NormSpecificity

Enum representing how specific a norm is. Used by `SpecificityResolution` — more specific norms win over general ones (lex specialis).

```java
public enum NormSpecificity {
    UNIVERSAL,    // applies to all cases/tenants
    DOMAIN,       // applies to a specific domain (e.g., clinical, financial)
    TENANT,       // applies to a specific tenant
    CASE_TYPE,    // applies to a specific case definition
    INSTANCE;     // applies to a specific case instance

    public boolean isMoreSpecificThan(NormSpecificity other) {
        return this.ordinal() > other.ordinal();
    }
}
```

### ResolutionMethod

Enum identifying which strategy produced the resolution. For audit trail.

```java
public enum ResolutionMethod {
    PRIORITY,
    SPECIFICITY,
    RECENCY,
    MOST_RESTRICTIVE,
    ESCALATION
}
```

### NormDecision<T>

A norm source's decision wrapped with metadata for conflict resolution.

```java
public record NormDecision<T>(
    String source,
    T decision,
    int priority,
    NormSpecificity specificity,
    Instant establishedAt
) {}
```

### NormResolution<T>

The outcome of conflict resolution — who won, who was overridden, and why.

```java
public record NormResolution<T>(
    NormDecision<T> winner,
    List<NormDecision<T>> overridden,
    String reason,
    ResolutionMethod method
) {}
```

### ConflictResolutionStrategy<T>

The core SPI. Given a list of conflicting norm decisions, determine which one wins.

```java
@FunctionalInterface
public interface ConflictResolutionStrategy<T> {
    NormResolution<T> resolve(List<NormDecision<T>> conflicting);
}
```

### Provided implementations

| Class | Strategy | Method |
|-------|----------|--------|
| `PriorityResolution<T>` | Highest priority wins (lowest int value = highest priority) | PRIORITY |
| `SpecificityResolution<T>` | Most specific norm wins (lex specialis) | SPECIFICITY |
| `RecencyResolution<T>` | Most recently established norm wins (lex posterior) | RECENCY |
| `MostRestrictiveResolution` | Typed to `RiskDecision` — replicates ChainedActionRiskClassifier's current behavior. Compares GateRequired narrowness. | MOST_RESTRICTIVE |
| `EscalationResolution<T>` | Always returns a designated escalation decision when any conflict exists | ESCALATION |

`MostRestrictiveResolution` is the only implementation typed to `RiskDecision` rather than generic `T`. It provides backward compatibility with the existing chain behavior.

### Conflict detection

The SPI assumes the caller has already detected a conflict — it receives a list of conflicting decisions and resolves them. Conflict detection (determining whether decisions actually contradict) is the caller's responsibility. For RiskDecision, contradiction means at least one Autonomous and at least one GateRequired.

A utility method on the strategy or a companion class can provide detection:

```java
public static boolean hasConflict(List<NormDecision<RiskDecision>> decisions) {
    boolean hasAutonomous = decisions.stream().anyMatch(d -> d.decision() instanceof RiskDecision.Autonomous);
    boolean hasGateRequired = decisions.stream().anyMatch(d -> d.decision() instanceof RiskDecision.GateRequired);
    return hasAutonomous && hasGateRequired;
}
```

## Test Coverage

| Test | What it verifies |
|---|---|
| PriorityResolutionTest | Highest priority wins, tie-breaking by position |
| SpecificityResolutionTest | Most specific wins, NormSpecificity ordering |
| RecencyResolutionTest | Most recent establishedAt wins |
| MostRestrictiveResolutionTest | Matches ChainedActionRiskClassifier behavior |
| EscalationResolutionTest | Always escalates on any conflict |

## File Inventory

10 production files, 5 test files. All in `io.casehub.blocks.normative`.

## Scope Exclusions

- Engine integration — wiring into ChainedActionRiskClassifier requires engine-api changes
- Composite strategies — combining multiple resolution strategies (e.g., priority then specificity as tiebreaker) is a natural next step but not needed without a consumer
- Conflict detection infrastructure — the SPI resolves pre-detected conflicts; systematic detection is an engine concern
