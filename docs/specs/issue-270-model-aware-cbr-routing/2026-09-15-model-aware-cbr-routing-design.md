# Model-Aware CBR Routing — Signal Provider Design

**Issue:** casehubio/blocks#270
**Date:** 2026-09-15
**Scope:** 1 new `RoutingSignalProvider` + 1 outcome weights SPI, engine prerequisite issue filed

## Problem

`CbrAgentRoutingStrategy` scores workers by `workerId` alone via `ExperienceAnalyser.workerSuccessRates()`. When the same agent runs on different LLM models (via ModelRegistry resolution), the outcome is attributed to the agent, not the model. CBR can't learn that agent X performs better on `claude-sonnet` than on `gpt-4o` for a given capability.

## Solution

A new `ModelPreferenceSignalProvider` implementing `RoutingSignalProvider` — composable via `RoutingSignalAssembler`, following the established pattern of `PredecessorAnalyser`, `CoordinationSignalProvider`, and `DispositionAwareRouting`.

### ModelPreferenceSignalProvider

```java
@ApplicationScoped
public class ModelPreferenceSignalProvider implements RoutingSignalProvider {

    @Override
    public String id() { return "model-preference"; }

    @Override
    public @Nullable RoutingSignal score(AgentRoutingContext context,
                                          List<AgentCandidate> candidates) {
        // 1. Extract (workerId, modelId) pairs from experience plan steps
        //    where parameters contain "modelId" key
        // 2. Group by compound key, compute weighted success rate using
        //    CbrOutcomeWeights (same weights as base CBR scoring)
        // 3. For each candidate, find the (workerId, bestModelId) with
        //    highest success rate
        // 4. Return signal with score per candidate and recommended
        //    modelId in the reason field
    }
}
```

**Integration:** Discovered via CDI as an `@ApplicationScoped` `RoutingSignalProvider`. The existing `RoutingSignalAssembler` composites it alongside other signal providers. No changes to `CbrAgentRoutingStrategy` needed — it already reads from the assembler (line 131-141).

### Data flow

```
Engine records invocation → ResolutionStep.parameters("modelId", "claude-sonnet-4")
  → CbrRetrievalService maps to ExperiencePlanStep.parameters("modelId", ...)
    → ModelPreferenceSignalProvider reads from experience plan steps
      → Scores by (workerId, modelId) compound key
        → RoutingSignal with recommended model per candidate
```

**Engine prerequisite:** casehubio/engine#1096 — record `modelId` in `ResolutionStep.parameters()`. Until this lands, the signal provider returns null (no model data in experiences).

### Scoring algorithm

For each retrieved experience with plan steps containing `modelId` in parameters:

1. Extract `(workerId, modelId, outcome, similarity)` tuples
2. Filter to steps matching the target `capabilityName` and eligible candidate IDs
3. Group by `(workerId, modelId)` compound key
4. For each group: compute `weightedScore = sum(outcomeWeight * similarity) / sum(similarity)`
5. For each candidate: find the `modelId` with the highest weighted score
6. Return `RoutingSignal.CandidateSignal.Score` with the best compound score, `reason` carries `"model-preference:modelId"`

Uses `ModelPreferenceOutcomeWeights` SPI (with `@DefaultBean` default using same weights as `CbrOutcomeWeights`: SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, FAILURE=0.0).

### Cold start

No cold-start logic. When no model data exists for a candidate, the signal provider returns null — base CBR score and other signals determine selection. The engine's static model policy (eidos#172 vocabulary-based selection) handles initial assignment. Model preference emerges from accumulated outcomes over time.

### What this does NOT do

- Does NOT modify `CbrAgentRoutingStrategy` — it's a composable signal, not an integrated change
- Does NOT handle model exploration/exploitation — that's an engine-level policy concern
- Does NOT require changes to `ExperiencePlanStep` or `RetrievedExperience` — reads from existing `parameters` map

## Testing

Plain JUnit 5:

- `ModelPreferenceSignalProviderTest`:
  - No model data → returns null
  - Single model per worker → scores by outcome weight
  - Multiple models per worker → recommends highest-scoring model
  - Candidate not in experiences → excluded from signal
  - Similarity weighting → closer experiences have more influence
- `ModelPreferenceOutcomeWeightsTest`: default weights match CbrOutcomeWeights

## References

- `CbrAgentRoutingStrategy.java:131-141` — signal assembler integration point
- `PredecessorAnalyser.java` — existing RoutingSignalProvider pattern
- `CoordinationSignalProvider.java` — existing RoutingSignalProvider pattern
- `ExperiencePlanStep.java:27` — `parameters` map (data carrier)
- `CbrRetrievalService.java:386-413` — parameters pass-through
- `CbrOutcomeWeights.java` — weight SPI pattern
- casehubio/engine#1096 — engine prerequisite (record modelId)
- casehubio/eidos#172 — static model selection policy (cold start)
- casehubio/platform#285 — ModelRegistry epic
