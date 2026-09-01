# Goal Priority Escalation + Cross-Axis Composition — Design Spec

**Issues:** casehubio/blocks#166, #160, #159
**Date:** 2026-09-01
**Branch:** issue-166-goal-priority-escalation
**Repos:** blocks (primary), engine (DefaultGoalFormationService guard)

## Overview

Layer 3 of the autonomous intelligence stack — two capabilities deferred from Layer 2 (#136 D4, D6) to Layer 3 (#142 D11):

1. **Governed priority escalation** — drive-sourced goals can escalate from SECONDARY to PRIMARY when sustained narrative alignment provides provenance. Governance contract: sustained alignment across synthesis cycles, revocability when alignment drops, configurable thresholds.

2. **Cross-axis goal composition** — compound goals from multiple concurrent drive signals when a DerivedTheme has significant positive weights across multiple axes (e.g., "learn about X by engaging with Y" = curiosity + affiliation).

Both extend `GoalProposalOrchestrator` as pipeline steps. No engine-api SPI needed — blocks owns all inputs (NarrativeState, DriveProfile, DriveGoalProposal), and DefaultGoalFormationService already respects `suggestedPriority` on `ProposedGoal`.

## Architecture

```
Scheduler (consumer — quarkmind, claudony)
│
│  Tick ordering (D10 from #142):
│    1. Source orchestrators
│    2. NarrativeSynthesiser (if triggered)
│    3. NarrativeOrchestrator
│    4. DriveOrchestrator
│    5. GoalProposalOrchestrator ← THIS SPEC
│    6. Scheduler applies GoalProposalTick.Changes:
│       → register proposals via GoalFormationService
│       → remove abandoned goals via AgentRegistry
│       → apply priority adjustments via AgentGoal.toBuilder()
│       → apply governance attribute updates via AgentGoal.toBuilder()
│
GoalProposalOrchestrator pipeline (extended):
│
│  ┌─ Phase 1: Per-axis mapping (existing) ──────────────────────────
│  │  DriveGoalFormationStrategy (LLM, optional) → DriveGoalMapper (heuristic)
│  │  Per-axis proposals: one per active drive above proposalThreshold
│  │
│  ├─ Phase 2: Cross-axis composition (NEW — D2) ───────────────────
│  │  Detect DerivedThemes with ≥2 positive axis weights > crossAxisMinWeight
│  │  Compose compound DriveGoalProposals:
│  │    axis = dominant (highest positive weight, D6)
│  │    driveIntensity = dominant axis's DriveIntensity.intensity() (R1-06)
│  │    goalName = "compound-{dominant}-{secondary}-{themeLabel}" (R1-15)
│  │    proposalAttributes includes crossAxisWeights
│  │  Optional LLM enrichment via Instance<CrossAxisGoalEnricher>
│  │
│  ├─ Phase 3: Escalation evaluation (NEW — D1, D3, D5) ────────────
│  │  GoalEscalationPolicy.evaluate() for each proposal
│  │  Synthesis-cycle tracking: escalate after N confirmed cycles
│  │  Cap: 1 PRIMARY drive-sourced goal at a time
│  │
│  ├─ Phase 4: Demotion evaluation (NEW — D3) ──────────────────────
│  │  Check existing PRIMARY drive-sourced goals for alignment loss
│  │  Theme-specific demotion (R1-12): checks the ORIGINAL escalation
│  │    theme (from escalation.theme attribute), not any theme
│  │  Synthesis-cycle tracking: demote after M confirmed misaligned cycles
│  │  Emit PriorityAdjustment + GovernanceAttributeUpdate signals
│  │
│  └─ Phase 5: Ranking + capacity (existing) ───────────────────────
│     Sort by driveIntensity, cap at maxDriveGoals
│
│  Output: GoalProposalTick.Changes (D4)
│    - newProposals: List<DriveGoalProposal>
│    - abandonedGoalNames: List<String>
│    - priorityAdjustments: List<PriorityAdjustment>
│    - governanceUpdates: List<GovernanceAttributeUpdate>
│
└── DefaultGoalFormationService guard (D7)
    Rejects PRIMARY on drive-sourced goals without escalatedBy provenance
```

### NarrativeOrchestrator as Optional Dependency (D8)

GoalProposalOrchestrator gains `Instance<NarrativeOrchestrator>` and `GoalEscalationConfig` — same `Instance<>` pattern as `Instance<GoalSignalStore>` for the orchestrator, direct injection for config (R1-09). Config is shared between the orchestrator (cross-axis thresholds, cycle counts) and the policy (alignment thresholds). When narrative is absent, phases 2-4 are skipped: no narrative context means no cross-axis composition, no escalation, no demotion. Drive-sourced goals remain SECONDARY.

When present, `currentNarrative(agentId, tenantId)` provides the NarrativeState for:
- Cross-axis theme detection (Phase 2)
- GoalEscalationContext construction (Phase 3)
- Demotion alignment checks (Phase 4)

## Type System

### Package: `io.casehub.blocks.agentic.social.goal`

#### GoalEscalationPolicy (SPI)

```java
@FunctionalInterface
public interface GoalEscalationPolicy {
    @Nullable EscalationResult evaluate(DriveGoalProposal proposal,
                                         GoalEscalationContext context);
}
```

#### EscalationResult (record)

```java
public record EscalationResult(
        GoalPriority priority,
        String themeLabel,
        String reason) {
    public EscalationResult {
        Objects.requireNonNull(priority);
        Objects.requireNonNull(themeLabel);
        Objects.requireNonNull(reason);
    }
}
```

#### GoalEscalationContext (record)

```java
public record GoalEscalationContext(
        NarrativeState narrative,
        DriveProfile drives,
        AgentDescriptor descriptor) {
    public GoalEscalationContext {
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(drives);
        Objects.requireNonNull(descriptor);
    }
}
```

#### NarrativeGoalEscalationPolicy (implementation)

```java
@ApplicationScoped
public class NarrativeGoalEscalationPolicy implements GoalEscalationPolicy {

    private final GoalEscalationConfig config;

    // CDI + test constructors

    @Override
    public @Nullable EscalationResult evaluate(DriveGoalProposal proposal,
                                                GoalEscalationContext context) {
        // Guard: cap check using configurable maxPrimaryDriveGoals
        if (countPrimaryDriveGoals(context.descriptor()) >= config.maxPrimaryDriveGoals())
            return null;

        // Collect all qualifying themes, return strongest (R1-04)
        DerivedTheme bestTheme = null;
        double bestScore = 0.0;

        for (var theme : context.narrative().themes()) {
            if (theme.salience() <= config.escalationSalienceThreshold()) continue;

            Double weight = theme.axisModulationWeights().get(proposal.axis());
            if (weight == null || weight <= config.minAxisAlignmentWeight()) continue;
            // Sign-aware: positive weight only — negative = suppression, disqualifies

            double score = theme.salience() * weight;
            if (score > bestScore) {
                bestScore = score;
                bestTheme = theme;
            }
        }

        if (bestTheme == null) return null;

        Double bestWeight = bestTheme.axisModulationWeights().get(proposal.axis());
        return new EscalationResult(
                GoalPriority.PRIMARY,
                bestTheme.label(),
                "goal aligns with identity theme '" + bestTheme.label()
                + "' (salience=" + String.format("%.2f", bestTheme.salience())
                + ", axisWeight=" + String.format("%.2f", bestWeight) + ")");
    }
}
```

**Same-tick cap enforcement** (R1-11): When multiple proposals qualify for PRIMARY in a single tick, the orchestrator post-processes after all policy evaluations: sorts escalated proposals by `driveIntensity` descending, keeps only the top `maxPrimaryDriveGoals` at PRIMARY, reverts the rest to SECONDARY (clearing their `proposalAttributes`).

**Cross-axis escalation check** (R1-07): For cross-axis proposals, the orchestrator verifies that all contributing axes (from `crossAxisWeights`) have positive alignment with the escalation theme before accepting the `EscalationResult`. If the escalation theme suppresses any contributing axis, the escalation is rejected for that proposal.

#### GoalEscalationConfig (record)

```java
public record GoalEscalationConfig(
        double escalationSalienceThreshold,
        double minAxisAlignmentWeight,
        double crossAxisMinWeight,
        int minCrossAxisCount,
        int escalationCycles,
        int demotionCycles,
        int maxPrimaryDriveGoals) {

    public GoalEscalationConfig {
        if (escalationSalienceThreshold < 0.0 || escalationSalienceThreshold > 1.0)
            throw new IllegalArgumentException("escalationSalienceThreshold must be in [0, 1]");
        if (minAxisAlignmentWeight < 0.0 || minAxisAlignmentWeight > 1.0)
            throw new IllegalArgumentException("minAxisAlignmentWeight must be in [0, 1]");
        if (crossAxisMinWeight < 0.0 || crossAxisMinWeight > 1.0)
            throw new IllegalArgumentException("crossAxisMinWeight must be in [0, 1]");
        if (minCrossAxisCount < 2)
            throw new IllegalArgumentException("minCrossAxisCount must be >= 2");
        if (escalationCycles < 1)
            throw new IllegalArgumentException("escalationCycles must be >= 1");
        if (demotionCycles < 1)
            throw new IllegalArgumentException("demotionCycles must be >= 1");
        if (maxPrimaryDriveGoals < 0)
            throw new IllegalArgumentException("maxPrimaryDriveGoals must be >= 0");
    }

    public static GoalEscalationConfig defaults() {
        return new GoalEscalationConfig(
                0.6,   // escalationSalienceThreshold
                0.3,   // minAxisAlignmentWeight
                0.3,   // crossAxisMinWeight
                2,     // minCrossAxisCount
                2,     // escalationCycles
                2,     // demotionCycles
                1);    // maxPrimaryDriveGoals
    }
}
```

#### PriorityAdjustment (record)

```java
public record PriorityAdjustment(
        String goalName,
        GoalPriority newPriority,
        String reason) {
    public PriorityAdjustment {
        Objects.requireNonNull(goalName);
        Objects.requireNonNull(newPriority);
        Objects.requireNonNull(reason);
    }
}
```

#### GovernanceAttributeUpdate (record)

```java
public record GovernanceAttributeUpdate(
        String goalName,
        Map<String, String> attributeUpdates) {
    public GovernanceAttributeUpdate {
        Objects.requireNonNull(goalName);
        attributeUpdates = Map.copyOf(attributeUpdates);
    }
}
```

#### CrossAxisGoalEnricher (SPI)

Optional LLM enrichment for cross-axis goal descriptions.

```java
@FunctionalInterface
public interface CrossAxisGoalEnricher {
    @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristicProposal,
                                        NarrativeState narrative,
                                        DerivedTheme sourceTheme);
}
```

#### LlmCrossAxisGoalEnricher (implementation)

```java
@ApplicationScoped
public class LlmCrossAxisGoalEnricher implements CrossAxisGoalEnricher {

    private final AgentProvider agentProvider;

    // CDI + test constructors

    @Override
    public @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristic,
                                               NarrativeState narrative,
                                               DerivedTheme sourceTheme) {
        // Build prompt with theme context, per-axis weights, narrative episodes
        // LLM generates richer goalDescription + formationReason
        // Return new DriveGoalProposal with enriched text, same axis/intensity
        // Graceful degradation: return null on LLM failure → heuristic used
    }
}
```

### Modified Types

#### GoalProposalTick (sealed interface — modified)

```java
public sealed interface GoalProposalTick {
    record NoChange(@Nullable String reason) implements GoalProposalTick {}
    record Changes(
            List<DriveGoalProposal> newProposals,
            List<String> abandonedGoalNames,
            List<PriorityAdjustment> priorityAdjustments,
            List<GovernanceAttributeUpdate> governanceUpdates
    ) implements GoalProposalTick {}
}
```

Renamed from `Proposed` → `Changes`. Zero production callers (verified via call hierarchy — only test callers).

#### DriveGoalProposal (record — modified)

```java
public record DriveGoalProposal(
        DriveAxis axis,
        String goalName,
        String goalDescription,
        String formationReason,
        double driveIntensity,
        @Nullable GoalPriority suggestedPriority,      // NEW — null = SECONDARY
        @Nullable Map<String, String> proposalAttributes  // NEW — escalation provenance (R1-02)
) { ... }
```

Existing 5-arg constructor delegates to 7-arg with `null` priority and `null` attributes. The orchestrator populates `proposalAttributes` with escalation provenance (`escalatedBy`, `escalation.theme`, `escalation.escalatedAt`, `escalation.firstAlignedSynthesisAt`) when a proposal is escalated to PRIMARY. The scheduler merges `proposalAttributes` with the standard `source`/`driveAxis` attributes when building `GoalFormationProposal.ProposedGoal.attributes`. For cross-axis proposals, `crossAxisWeights` is also included in `proposalAttributes`.

### Governance Attribute Schema

Goal attributes used for escalation governance (persisted in AgentGoal.attributes via AgentRegistry):

| Key | Written when | Value |
|-----|-------------|-------|
| `source` | proposal registration | `"drive"` |
| `driveAxis` | proposal registration | e.g., `"CURIOSITY"` |
| `escalatedBy` | escalation to PRIMARY | `"narrative"` |
| `escalation.theme` | escalation to PRIMARY | theme label |
| `escalation.escalatedAt` | escalation to PRIMARY | ISO-8601 timestamp |
| `escalation.firstAlignedSynthesisAt` | escalation to PRIMARY | ISO-8601 timestamp |
| `escalation.lastAlignedSynthesisAt` | each aligned synthesis check | ISO-8601 timestamp |
| `escalation.misalignedCycleCount` | each misaligned synthesis check | integer as string |
| `crossAxisWeights` | cross-axis proposal | serialised map, e.g., `"CURIOSITY:0.4,AFFILIATION:0.5"` |

## Upstream Changes

| Repo | Change | Why |
|------|--------|-----|
| engine | `DefaultGoalFormationService.propose()` — add provenance guard: reject PRIMARY on drive-sourced goals without `escalatedBy` attribute (D7) | Defence-in-depth: engine enforces governance pathway |
| blocks | `GoalProposalTick.Proposed` → `Changes` with 4 signal types (D4) | Carries escalation/demotion/governance signals |
| blocks | `DriveGoalProposal` gains `@Nullable GoalPriority suggestedPriority` | Escalated proposals carry PRIMARY |
| blocks | `GoalProposalOrchestrator` gains `Instance<NarrativeOrchestrator>` dependency (D8) | Optional narrative context for escalation/composition |
| blocks | `GoalProposalOrchestrator.doTick()` — add phases 2-4 (D2, D1, D3) | Cross-axis composition, escalation, demotion pipeline |

All blocks changes are additive except the `GoalProposalTick` rename (zero production callers). The engine change is a single validation check — additive, no API change.

## Testing Strategy

### GoalEscalationPolicy tests (plain JUnit 5)

- Alignment: theme salience above threshold + positive axis weight above threshold → escalation
- No alignment: salience below threshold → null
- No alignment: axis weight negative (suppression) → null
- No alignment: axis weight below threshold → null
- Cap: count of PRIMARY drive-sourced goals >= maxPrimaryDriveGoals → null
- Provenance: EscalationResult carries theme label and formatted reason
- Strongest theme: when multiple themes qualify, returns highest salience × weight
- Cross-axis escalation: all contributing axes must have positive alignment with theme

### Synthesis-cycle tracking tests (Mockito)

- Escalation: first aligned synthesis → cycle count 1, not yet escalated
- Escalation: second aligned synthesis → cycle count 2, escalated to PRIMARY
- Reset: alignment lost between syntheses → cycle count resets to 0
- No synthesis: same `synthesisedAt` → no cycle increment
- Demotion: PRIMARY goal loses alignment for 2 synthesis cycles → PriorityAdjustment emitted
- Demotion: alignment restored during demotion window → counter resets
- Restart recovery: governance attributes read from AgentGoal.attributes on first tick

### Cross-axis composition tests (plain JUnit 5)

- Theme with 2 positive axis weights above threshold → compound proposal created
- Theme with 1 positive + 1 negative weight → not cross-axis (negative = suppression)
- Theme with 2 positive weights below threshold → not cross-axis
- Dominant axis: highest positive weight becomes `proposal.axis()`
- driveIntensity: uses dominant axis's DriveIntensity.intensity()
- goalName: "compound-{dominant}-{secondary}-{themeLabel}" format
- `crossAxisWeights` attribute: all contributing axes and weights serialised
- proposalAttributes: cross-axis proposals carry crossAxisWeights in proposalAttributes
- LLM enricher: when present and returns non-null → enriched description used
- LLM enricher: when present and returns null → heuristic description used
- LLM enricher: when absent → heuristic description used

### GoalProposalTick.Changes tests

- Changes carries all four signal types correctly
- Empty lists for absent signal types (no proposals but has adjustments)
- GovernanceAttributeUpdate merges into existing goal attributes

### DefaultGoalFormationService guard tests (Mockito)

- Drive-sourced + PRIMARY + escalatedBy present → accepted
- Drive-sourced + PRIMARY + escalatedBy absent → rejected with reason
- Drive-sourced + SECONDARY → accepted (no provenance required)
- Non-drive-sourced + PRIMARY → accepted (no provenance required)
- Null attributes + PRIMARY → accepted (not drive-sourced)

### GoalProposalOrchestrator integration tests (Mockito)

- Full pipeline: per-axis → cross-axis → escalation → ranking
- Narrative absent: phases 2-4 skipped, SECONDARY proposals only
- Same-tick cap: multiple proposals qualify for PRIMARY → only highest-intensity kept
- Demotion + escalation exclusivity: demotion in same tick blocks new escalation
- Theme-specific demotion: checks original escalation theme, not any qualifying theme
- proposalAttributes flow: escalation provenance merges into ProposedGoal.attributes

## What This Does NOT Include

- **Human approval escalation** — a future `HumanApprovalEscalationPolicy` composing with `OversightGateService`. The `GoalEscalationPolicy` SPI supports this (different implementation, same interface). File a GitHub issue to track this deferral from #160's scope (R1-16).
- **Engine-api SPI for escalation** — blocks-local per D1. If a future need arises for engine-level governance, the provenance guard (D7) can be extended.
- **DriveGoalProposal priority overhaul** — minimal change: one nullable field. No refactoring of the existing per-axis mapping pipeline.
- **Tick ordering changes** — D10 from #142 already defines NarrativeOrchestrator before GoalProposalOrchestrator. No scheduler changes needed.

## References

- [D11, #142 decisions] — Layer 2 deferrals: cross-axis composition and governed priority escalation
- [D4, #136 decisions] — Cross-axis composition deferred to Layer 3
- [D6, #136 decisions] — PRIMARY escalation rejected for Layer 2 (ungoverned)
- [D10, #142 decisions] — Tick ordering: narrative before drives before goals
- [GoalProposalOrchestrator.java] — existing compositor, evaluateMappers/evaluateRelevance patterns
- [DefaultGoalFormationService.java] — engine registration, suggestedPriority handling
- [NarrativeModulation.java] — NarrativeState → drive modulation conversion
- [DerivedTheme.java] — salience + axisModulationWeights (typed DriveAxis keys)
- [DriveComposer.java] — modulation algebra precedent (mood, personality, narrative)
- [AgentGoal.java] — attributes Map + Builder pattern for priority/attribute mutation
- [GoalFormationProposal.ProposedGoal] — suggestedPriority + attributes for provenance
- [ADR-0001-drive-compositor-pattern.md] — compositor: tick evaluates, no side effects
