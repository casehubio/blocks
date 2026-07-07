# Structured RoutingFeatureExtractor for Domain-Specific Routing

**Issue:** casehubio/blocks#33
**Parent:** #16 (RAG-enriched routing)
**Date:** 2026-07-06

## Problem

CBR routing uses text-only similarity. `TextOnlyFeatureExtractor` returns empty
features (`Map.of()`) and `caseContext.toString()` as problem text (raw JSON with
brackets, field names, structural noise). Every domain gets the same low-quality
similarity signal regardless of the rich structured data available in its case
context.

## Decision

No new infrastructure in blocks. The `RoutingFeatureExtractor` SPI is complete:
`extractFeatures()` returns `Map<String, Object>`, `extractProblem()` returns
`String`, CDI override via `@DefaultBean` displacement works — any
`@ApplicationScoped` domain implementation displaces the `@DefaultBean` base
automatically (per `alternative-extension-patterns.md` Pattern C). Jackson's
`JsonNode` API provides all the navigation needed. A builder or DSL would save ~5
lines per consumer while adding ~100 lines of abstraction in blocks for cases that
will need custom logic anyway.

Value delivery is in domain implementations.

## Deliverables

### 1. Blocks integration test

New test in `CbrAgentRoutingStrategyTest` demonstrating a custom
`RoutingFeatureExtractor` flowing structured features through the full CBR query
path. Verifies:

- Custom features appear in the `CbrQuery` passed to `CbrCaseMemoryStore`
- Custom problem text is used as the query's problem field
- Null problem text is handled (no `withProblem` call)

Serves as living documentation of the SPI contract for domain implementors.

### 2. AML feature extractor

**Class:** `io.casehub.aml.routing.AmlRoutingFeatureExtractor`
**Location:** `aml/app/src/main/java/io/casehub/aml/routing/`
**CDI:** `@ApplicationScoped`

Features extracted from `caseContext` JsonNode:

**Always available** (from initial context built by `AmlEngineCoordinator`):

| Feature key | Type | Source path |
|---|---|---|
| `knownHighRisk` | boolean | `/priorEntityContext/knownHighRisk` |
| `entityRiskCount` | int | `/priorEntityContext/entityRiskCount` |
| `networkCount` | int | `/priorEntityContext/networkCount` |
| `patternCount` | int | `/priorEntityContext/patternCount` |

**Available after entity-resolution capability completes** (absent for
first-capability routing):

| Feature key | Type | Source path |
|---|---|---|
| `entityType` | String | `/entityResolution/entityType` |
| `riskScore` | double | `/entityResolution/riskScore` |

The feature vector is intentionally sparse for early routing decisions — only prior
context features are present before entity-resolution runs. The null-safe extraction
pattern (include only where the JsonNode path exists) produces a variable-length
feature map that grows as the investigation progresses. CBR similarity handles
mixed-cardinality feature maps via the store's scoring algorithm.

Problem text adapts to available context:
- With entity data: `"entityType=PEP, riskScore=0.87, 3 prior entity risks"`
- Without: `"3 prior entity risks, 2 network patterns, known high risk"`
- Returns null when caseContext is null/empty.

**Test:** Unit test with constructed JsonNode covering both sparse
(pre-entity-resolution) and full feature vectors. No CDI container.

### 3. Clinical feature extractor

**Class:** `io.casehub.clinical.routing.ClinicalRoutingFeatureExtractor`
**Location:** `clinical/runtime/src/main/java/io/casehub/clinical/routing/`
**CDI:** `@ApplicationScoped`

| Feature key | Type | Source path | Notes |
|---|---|---|---|
| `ctcaeGrade` | int | `/grade` | Parsed from `CtcaeGrade` enum name (e.g., `"GRADE_3"` → `3`) |
| `unexpected` | boolean | `/unexpected` | |
| `suspected` | boolean | `/suspected` | |
| `hasPriorGrade3OrAbove` | boolean | `/patientContext/hasPriorGrade3OrAbove` | |
| `hasPriorEscalation` | boolean | `/patientContext/hasPriorEscalation` | |
| `aeCount` | int | `/patientContext/aeCount` | |
| `siteId` | String | `/siteId` | |

The `/grade` field stores the `CtcaeGrade` enum name (e.g., `"GRADE_3"`), not a
numeric value. The extractor parses the numeric suffix to produce an integer grade
(1–5) suitable for CBR similarity. Direct `asInt()` on the text node would silently
return 0 for all grades — the parsing is essential.

Problem text: `"Grade {N} AE, {unexpected/expected}, patient has {X} prior AEs"`.
Returns null when caseContext is null/empty.

**Test:** Unit test with constructed JsonNode. No CDI container.

## Relationship to consumer issues

The feature vocabularies in aml#61 and clinical#78 describe the full CBR lifecycle
(Retain + Retrieve + Reuse) and include aspirational features not yet available in
the routing case context:

**aml#61** features not extractable at routing time: `transaction_pattern` (requires
alert classification not in `SuspiciousTransaction`), `jurisdiction_flags` (not in
case context), `alert_source` (not in case context). Conceptual mappings:
`entity_risk_tier` ≈ `entityType` + `riskScore`, `network_size` ≈ `networkCount`.

**clinical#78** features not extractable at routing time: `ae_system_organ_class`
(MedDRA SOC not in AE escalation context), `trial_phase` (not in context),
`treatment_arm` (not in context). Conceptual mappings: `ae_severity_grade` ≈
`ctcaeGrade`, `prior_ae_count` ≈ `aeCount`.

This spec extracts what is actually available in each domain's case context today.
Enriching the context builders to surface additional features is follow-on work
tracked in the consumer issues.

## Cold-start behaviour

When these extractors are first deployed, existing CBR cases in the store were
recorded by `TextOnlyFeatureExtractor` with empty feature maps. New routing queries
will produce structured features, but historical cases have no feature keys to match
against.

This is graceful degradation, not a failure: all historical cases receive the same
zero feature-similarity score, so ranking among them is driven entirely by text
(vector) similarity — the same signal that drives routing today. Structured features
add differentiation only once new cases with features accumulate. No bulk migration
of historical cases is planned; gradual accumulation is the intended path.

The default `vectorWeight` of `0.5` means text similarity contributes half the total
score. Operators should verify that `minSimilarity` thresholds remain appropriate
after deployment, since effective similarity scores may shift when the feature
component contributes zero for historical cases.

## What this does NOT include

- No builder, DSL, or base class in blocks
- No feature weighting — `CbrAgentRoutingStrategy` passes empty weights (equal
  weight for all features). Adding domain-specific weights requires extending the
  `RoutingFeatureExtractor` SPI (e.g., `extractWeights()`) or adding weight
  configuration to the strategy. This is deferred, not trivially available
  (casehubio/blocks#37).
- No changes to the `RoutingFeatureExtractor` SPI itself
- No extractors for devtown or life (can follow the same pattern when needed)
