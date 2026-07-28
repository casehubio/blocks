# 0001 — CBR Coordination Memory Architecture

Date: 2026-07-28
Status: Accepted

## Context and Problem Statement

The CaseHub routing pipeline selects agents for capability steps based on individual agent performance (step-level success rates via `ExperienceAnalyser`) and case-level outcomes in multi-step plans (`PlanCompositionAnalyser`). Both score each worker independently — neither captures team composition effects: "Worker A + Worker B together produce better outcomes than Worker A + Worker C on this type of task." Issue qhorus#361 asks for CBR-informed routing that queries neocortex for historical outcomes of task+agent combinations, with adaptation-guided retrieval biased by team re-assembly feasibility.

## Decision Drivers

* The routing question is about teams, not individuals — the value proposition is team composition effects invisible to individual scoring
* Neocortex CBR already provides case storage, feature-based similarity, and retrieval infrastructure (`CbrCaseMemoryStore`, `CbrQuery`, `FeatureField.CategoricalList` for set overlap)
* The coordination signal should compose with existing routing signals (trust, individual competence, load) rather than replace them
* Existing experience cases (`PlanCbrCase`) already carry team composition data in their plan traces — each `PlanTrace` entry records `workerName`
* Existing retention (`CbrCaseRetainObserver`) already fires on case completion with access to all plan traces, outcomes, and features
* Pre-release maturity — breaking changes cost nothing; design for correctness, not backward compatibility

## Considered Options

* **Option A** — Enrich existing experience cases with team composition features + team-centric second query + `RoutingSignalProvider` (analysis-only)
* **Option B** — Extract team signals from existing `RetrievedExperience` plan traces without retrieval changes (analysis-only, no team-centric query)
* **Option C** — New coordination case type in neocortex + `RoutingSignalProvider` with direct CBR query
* **Option D** — Pairwise interaction records (one case per worker pair per case execution)

## Decision Outcome

Chosen option: **Option A**, because it reuses the existing data model and retention path (no new case type, no new retention mechanism), keeps the single retrieval pipeline (no direct `CbrCaseMemoryStore` injection from signal providers), and uses `CbrQuery` feature weighting — the mechanism CBR was designed for — to achieve team-centric retrieval as a second query angle on the same case store.

### Approach — Retention-First Design

**Retention (enrichment of existing path):**

`CbrCaseRetainObserver` already fires on `CaseOutcomeEvent`, builds `PlanCbrCase` with plan traces containing `workerName` per step, and stores with extracted features. The change: at retention time, extract additional coordination features from the plan traces before storing:

* `team_members` — `CategoricalList` of distinct worker IDs from the plan trace. Enables set-overlap similarity (Jaccard) for team-centric queries.
* `team_size` — `Numeric` count of distinct workers. Allows similarity weighting by team size.
* `capability_sequence` — `DiscreteSequence` of capability names in execution order. Enables edit-distance similarity for step-ordering patterns (future v2 dimension).

These features are added to the existing `PlanCbrCase.features()` map alongside the problem features extracted by the configured `CbrConfig.featureExtractor()`. No new case type, no new retention event, no separate lifecycle.

**Retrieval (second query via existing pipeline):**

Extend `CbrRetrievalService` to perform a second team-centric query when team features are present in the schema. The team-centric query uses the same `CbrQuery` mechanism but with different feature weights: `team_members` weighted heavily, problem features weighted lightly. This retrieves cases where similar teams worked on similar-enough tasks — a different retrieval angle on the same case store.

Both the problem-centric and team-centric results flow through the existing `CbrCaseMemoryStore` decorator chain (OutcomeWeighting, TrustWeighting, ScopeDecay, retrieval tracking) — no decorator bypass.

Extend `AgentRoutingContext` with a `coordinationExperiences()` accessor to deliver team-centric results alongside problem-centric results. This is a breaking API change — justified because the platform has no external callers and the change forces every consumer to be explicit about which experiences they use.

**Routing signal (analysis-only):**

New `CoordinationSignalProvider` implements `RoutingSignalProvider`, following the same pattern as `PlanCompositionAnalyser`: reads pre-retrieved data from `context.coordinationExperiences()`, scores eligible candidates based on historical team composition outcomes. No direct `CbrCaseMemoryStore` injection — the signal provider is analysis-only.

Adaptation-guided retrieval (AGR) — weighting cases by team re-assembly feasibility — is implemented in the signal provider, not in the decorator chain. AGR requires routing context (which candidates are currently eligible) that is not available at the decorator level. The signal provider receives both `AgentRoutingContext` and `List<AgentCandidate>`, which is exactly the context AGR needs.

### Positive Consequences

* No new case type — one case type, one retention path, one lifecycle (purge, supersession, erasure)
* Single retrieval pipeline — decorator chain applies uniformly to both problem-centric and team-centric queries
* Feature model extensibility — adding coordination dimensions (communication volume, convergence metrics) later is additive: new features in the schema and retention, no schema migration for existing cases
* Team composition becomes a first-class similarity dimension via CBR feature weighting — the mechanism it was designed for
* Composes with existing routing pipeline — coordination is one signal alongside individual competence, trust, and load
* Incremental activation — signal provider returns null when no coordination experiences exist

### Negative Consequences / Tradeoffs

* Two queries per routing decision when team features are configured — performance cost mitigated by existing retrieval caching (`CbrRetrievalTiming.CASE_LIFETIME`)
* `AgentRoutingContext` API change — adding `coordinationExperiences()` is breaking but the migration is mechanical
* AGR in signal provider rather than decorator — means AGR does not compose with other consumers of coordination data (acceptable: no other consumers exist, and if they emerge, the logic can be extracted)
* Communication metrics (message counts, convergence state) deferred to v2 — v1 uses plan trace data only, but the feature schema and "coordination" framing are designed for extension

## Pros and Cons of the Options

### Option A — Enrich existing cases + team-centric query + signal provider (chosen)

* Pro: No new case type — reuses PlanCbrCase, CbrCaseRetainObserver, CbrCaseMemoryStore lifecycle
* Pro: Single retrieval pipeline — decorator chain applies uniformly
* Pro: CbrQuery feature weighting is the canonical CBR mechanism for multi-angle retrieval
* Pro: Feature model is additive — communication metrics, step ordering can be added without migration
* Pro: Retention mechanism is already solved — CbrCaseRetainObserver has all needed data
* Con: Two queries per routing decision when team features are configured
* Con: Breaking change to AgentRoutingContext

### Option B — Extract from existing RetrievedExperience data (no retrieval change)

* Pro: No API changes, no retrieval changes — purely additive signal provider
* Pro: Immediately available — works with current pre-retrieved experiences
* Con: Retrieval is problem-centric — can only analyse team patterns in cases already retrieved for problem similarity
* Con: Misses cases where different problems had the same team — the key coordination insight
* Con: Cannot do adaptation-guided retrieval (no team-based query)

### Option C — New case type + direct CBR query from signal provider

* Pro: Clean separation — coordination cases are structurally distinct
* Con: Creates parallel retrieval path — bypasses decorator chain
* Con: Dual retention — two case types from the same execution event
* Con: Dual lifecycle — purge, supersession, erasure must handle both types
* Con: Data duplication — problem features, plan trace already in experience cases
* Con: Architectural precedent — future coordination-like queries each add their own path

### Option D — Pairwise interaction records

* Pro: Finer-grained — captures specific pair dynamics
* Con: O(n^2) cases per execution — 4-agent team produces 6 pair cases
* Con: Loses emergent team dynamics — a 4-agent team's behavior is not decomposable into 6 pairs
* Con: Harder similarity matching — what is the "problem" for a pair?
* Con: Contradicts POCBR literature — FlexiTeam, NaCoDAE, ABARC all use case-level team representation

## Links

* [qhorus#361 — CBR-informed agent routing + cross-agent coordination memory](https://github.com/casehubio/qhorus/issues/361)
* [qhorus#352 — Cross-Repo Coordination Improvements (parent)](https://github.com/casehubio/qhorus/issues/352)
* [FlexiTeam: POCBR for Team Organization (ICCBR 2022)](https://ceur-ws.org/Vol-3389/ICCBR_2022_Workshop_paper_90.pdf)
* [Two-Agent CBR / EAGR (ResearchGate)](https://www.researchgate.net/publication/393158642)
* [CBR for LLM Agents Review (arXiv 2504.06943)](https://arxiv.org/abs/2504.06943)
* [CAST: Case-Based Calibration (arXiv 2605.15041)](https://arxiv.org/abs/2605.15041)
* [Memento: Memory-Augmented Agents (arXiv 2508.16153)](https://arxiv.org/abs/2508.16153)
* [AGR via Graphical Models (arXiv 1905.12464)](https://arxiv.org/abs/1905.12464)
