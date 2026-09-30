# Decisions — issue-241-decision-narratives

## D1: Persistent state for SummarisationRunner

**Choice:** Deferred to #259 (identity narrative retrofit). Decision narrative pipeline uses in-memory per-key state via enhanced `KeyedSummarisationRunner` (see D7).
**Alternatives:**
- Externalize state entirely (no cache) — adds latency on hot path for consumers who don't need persistence
- Post-tick callback — doesn't help with load-on-startup, state lost on restart
- `StateStore<S>` SPI injected into `SummarisationRunner` as optional constructor parameter (original choice — deferred because no #241 consumer needs persistence)
**Rationale:** The decision narrative pipeline uses `KeyedSummarisationRunner` with in-memory per-key state management. `SummarisationRunner` already manages state in-memory via `partitionState` ConcurrentHashMap. StateStore's primary use case (persistence for identity narratives via `NarrativeMemory`) belongs in #259 when the actual consumer is being built and the SPI contract can be validated against real persistence needs.
  **Trade-offs:** Decision narrative state is volatile — lost on restart. Acceptable because decision narratives are re-derivable from platform signals.
  **Sources:** `SummarisationRunner.java:20` (partitionState ConcurrentHashMap), `CbrNarrativeStore.java` (existing CBR persistence pattern)
**Exploration:** quick
  **Status:** revised (R1-02: deferred to #259)

## D2: Richer gating via EmissionPolicy

**Choice:** Deferred to #259 (identity narrative retrofit). Decision narrative pipeline uses `completionTest` on `KeyedSummarisationRunner` (L1) and `completionTest` + `staleTimeout` on `KeyedSummarisationRunner` (L2) — existing gating mechanisms are sufficient.
**Alternatives:**
- `EmissionPolicy<IN, S>` SPI replacing `WindowPolicy` (original choice — deferred because decision narrative pipeline doesn't need it)
- Layered policies (WindowPolicy base + GatePolicy overlay)
- Extend WindowPolicy with optional predicates
**Rationale:** L1 uses `KeyedSummarisationRunner` with `completionTest` predicate, which is already richer than WindowPolicy — it gates on per-group completion semantics. L2 uses `KeyedSummarisationRunner` keyed by `caseId` with `completionTest` + `staleTimeout` for per-case grouping (see D7). The motivating use case for EmissionPolicy (`NarrativeSynthesisGate` — count + novelty + quiet period) is an identity narrative concern in `NarrativeSynthesiser`. Introduce EmissionPolicy in #259 when it can be validated against the real consumer.
**Trade-offs:** YAML pipeline surface compatibility preserved — `PipelineCompiler` continues producing `WindowPolicy` directly.
**Sources:** `WindowPolicy.java`, `KeyedSummarisationRunner` (completionTest predicate), `NarrativeSynthesisGate.java` (#259 use case)
**Exploration:** quick
**Status:** revised (R1-02: deferred to #259; R2-01: updated L2 description to match D7 — KeyedSummarisationRunner, not SummarisationRunner)

## D3: Post-summarisation output processing

**Choice:** Deferred to #259 (identity narrative retrofit). Decision narrative pipeline does not need post-summarisation pruning.
**Alternatives:**
- `OutputProcessor<OUT, S>` SPI on `SummarisationRunner` (original choice — deferred because no #241 consumer needs it)
- Let Summariser implementations handle pruning internally
**Rationale:** `DecisionNarrative` records are bounded by their source signals (finite per step/case), not by accumulated count. The motivating use case is `NarrativeSynthesiser.pruneEpisodes`/`pruneThemes` (lines 421-438) — identity narrative capacity enforcement. Introduce OutputProcessor in #259 alongside the identity narrative retrofit when the SPI contract can be validated against the actual pruning consumer.
**Trade-offs:** None for #241. Decision narratives are naturally bounded.
**Sources:** `NarrativeSynthesiser.java:191-192` (pruneEpisodes, pruneThemes — #259 use case)
**Exploration:** quick
**Status:** revised (R1-02: deferred to #259)

## D4: Defer identity narrative retrofit

**Choice:** Build decision narratives on the enhanced pipeline now. Retrofit identity narratives (`NarrativeSynthesiser`) in a separate issue (#259), queued after #241 in the .plan.
**Alternatives:**
- Retrofit now — proves both use cases in one shot but doubles scope, makes debugging harder
**Rationale:** Decision narratives prove the pipeline enhancements on greenfield code. No risk of breaking the existing social cognition stack. Identity narrative retrofit (#259) references this work as proof the pattern works.
**Trade-offs:** Two narrative systems coexist temporarily — `NarrativeSynthesiser` (identity) and the summarisation pipeline (decision). Unified after #259.
**Sources:** `NarrativeSynthesiser.java` (working, tested, consumed by SocialAvatarCognition), blocks#259
**Exploration:** quick
**Status:** captured

## D5: Decision signal type design

**Choice:** Single sealed `DecisionSignal` hierarchy as pipeline input
**Alternatives:**
- Heterogeneous input with multiple `EventStreamBus` sources merged — loses type safety, summariser receives untyped payloads
**Rationale:** `sealed interface DecisionSignal` with variants: `RoutingDecision`, `CbrRetrieval`, `TrustAssessment`, `DeliberationOutcome`, `StepOutcome`. Each is a record carrying relevant data from its platform source. Flows as `LevelEvent<DecisionSignal>` into the pipeline. `NarrativeSignalStrategy` SPI produces these events from platform sources — domain repos (fsitrading) implement it. Exhaustive `switch` matching in the summariser.
**Trade-offs:** All signal types must implement `DecisionSignal` — new signal sources require adding a permit to the sealed interface. But sealed hierarchies are the project's standard pattern for this.
**Sources:** casehubio/blocks#241 (issue body — listed 5 fragment types, reinterpreted as signal types), `NarrativeFragment.java` (existing sealed pattern)
**Exploration:** quick
**Status:** captured

## D6: Pipeline output type

**Choice:** Structured `DecisionNarrative` record — separate from `NarrativeFragment`
**Alternatives:**
- Prose String output — simplest but loses structure for timeline/UI consumption
- Extend NarrativeFragment with DecisionEpisode variant — conflates identity and operational concerns, every NarrativeFragment consumer gains an unwanted switch branch
**Rationale:** New record: `DecisionNarrative(caseId, stepName, explanation, evidenceSources, confidence, producedAt)`. Lives in its own package — NOT in social cognition `narrative` package. Pipeline is `SummarisationRunner<DecisionSignal, DecisionNarrative>`. Timeline component and Ops Centre consume `DecisionNarrative` directly. Clean separation: `NarrativeFragment` = identity narrative, `DecisionNarrative` = decision explanation.
**Trade-offs:** Two separate narrative type systems. But they serve different purposes (reflective identity vs. operational explanation) and have different consumers.
**Sources:** `NarrativeFragment.java` (sealed hierarchy to NOT extend), `NarrativePromptSection.java` (existing consumer undisturbed)
**Exploration:** quick
**Status:** captured

## D7: Pipeline topology

**Choice:** Two-level pipeline — L1 per-step heuristic accumulation, L2 per-case LLM synthesis
**Alternatives:**
- Three levels (add L3 per-case arc summary) — premature, can be added later if needed
- Single level (flat LLM) — sends every raw signal to LLM, high cost, noisy input
**Rationale:** L1: raw `DecisionSignal` events grouped by `caseId + stepName` via `KeyedSummarisationRunner`. Heuristic summariser — no LLM, microsecond latency. Produces `StepDecisionSummary` (see D9). L2: `StepDecisionSummary` accumulated per case via `KeyedSummarisationRunner` keyed by `caseId`, `ContentSummariser<StepDecisionSummary, DecisionNarrative>` — LLM-backed with `@Nullable` previous state. L1→L2 split keeps LLM calls batched and infrequent.
**Prerequisite:** `KeyedSummarisationRunner` must be enhanced to support `StatefulSummariser` with per-key state management. Currently it always calls `summariser.summarise(batch)` (stateless), ignoring `StatefulSummariser`. The fix mirrors `SummarisationRunner.invokeSummariser()` (line 114) — add `instanceof StatefulSummariser` check with per-key state in a `ConcurrentHashMap<K, Object>`. Without this, L2's `ContentSummariser.asSummariser()` always receives null previous state.
**Trade-offs:** L1 heuristic summariser must be written per domain (or generically). Two-level adds wiring complexity vs. single level. But cost savings from not sending raw signals to LLM outweigh this.
**Sources:** `ClinicalPipelineTest.java` (multi-level pipeline precedent), `KeyedSummarisationRunner.java` (grouping by key), `SummarisationRunner.invokeSummariser()` (StatefulSummariser pattern to replicate)
**Exploration:** quick
**Status:** revised (R1-03: added StatefulSummariser prerequisite, clarified L2 uses KeyedSummarisationRunner keyed by caseId)

## D8: YAML example scope

**Choice:** Self-contained test example in blocks test sources, fsitrading-inspired domain signals
**Alternatives:**
- Separate examples/ module with runnable mini-app — heavier, breaks existing convention of test-class examples
**Rationale:** Like existing `ClinicalPipelineTest` and `LogisticsPipelineTest` in `src/test/java/.../summarisation/examples/`. Uses fsitrading-inspired signal types (trading decisions, analyst routing, trade outcomes) defined locally in the test — no dependency on fsitrading. Test defines its own sealed hierarchy (`TradingSignal`) exercising the same pattern (sealed → exhaustive switch → structured output) without coupling to production `DecisionSignal`. Pipeline primitives are generic — `KeyedSummarisationRunner<K, IN, OUT>` works identically regardless of payload type. YAML pipeline definition lives alongside the test. Proves pipeline end-to-end. When fsitrading wires up for real (Batch 4), it follows this pattern with actual types.
**Trade-offs:** Example is a test, not a standalone app — less visible to newcomers. But matches existing convention.
**Sources:** `ClinicalPipelineTest.java`, `LogisticsPipelineTest.java` (existing example convention)
**Exploration:** quick
**Status:** captured

## D9: StepDecisionSummary intermediate type

**Choice:** Structured `StepDecisionSummary` record as L1→L2 intermediate representation
**Alternatives:**
- Unstructured prose string — loses structure, makes L2 prompt fragile and domain-specific
- Pass raw `DecisionSignal` events to L2 — defeats the purpose of L1 heuristic summarisation
**Rationale:** `StepDecisionSummary(String caseId, String stepName, List<SignalDigest> signals, Instant from, Instant to)` where `SignalDigest(String signalType, String summary, Map<String, String> keyFacts, double confidence)`. The L1 heuristic summariser flattens sealed `DecisionSignal` variants into `SignalDigest` — extracting key facts (routing scores, CBR similarity, trust levels, deliberation outcomes) as structured strings. This makes the L2 LLM prompt domain-agnostic: it receives structured signal digests, not raw platform types. The `keyFacts` map is intentionally stringly-typed — it's LLM input, not programmatic API.
**Trade-offs:** L1 heuristic summariser must know how to extract key facts from each `DecisionSignal` variant. New signal types require updating the summariser (but sealed switch enforcement guarantees this).
**Sources:** D7 (pipeline topology), D5 (DecisionSignal hierarchy)
**Exploration:** surfaced by review (R1-05)
**Status:** captured

## D10: Signal integration model

**Choice:** Observer-driven — `NarrativeSignalStrategy` implements `StepOutcomeObserver` and `CaseOutcomeObserver` platform SPIs
**Alternatives:**
- Push model via `EventStreamBus<DecisionSignal>` pub/sub — requires platform systems to know about `DecisionSignal` types, creates coupling in the wrong direction
- Pull model with tick-cycle polling — adds latency, wastes cycles when no new signals exist
**Rationale:** `StepOutcomeObserver` (`io.casehub.api.spi`) and `CaseOutcomeObserver` (`io.casehub.api.spi`) are existing platform SPI interfaces with implementations (`NoOpStepOutcomeObserver`, `NoOpCaseOutcomeObserver`) and test infrastructure. The observer model is event-driven — no polling. When the engine completes a step or case, the observer receives the outcome, converts it to `DecisionSignal` variants, and publishes `LevelEvent<DecisionSignal>` events to the pipeline's input `EventStreamBus`. `NarrativeSignalStrategy` now extends both `StepOutcomeObserver` and `CaseOutcomeObserver` directly so CDI auto-discovers it. **Note:** Only `StepOutcome` is populatable from existing platform SPIs. Routing decisions, CBR retrievals, trust assessments, and deliberation outcomes require platform observer SPI extensions that do not yet exist (see spec §Signal source availability). The original assertion that these signals are "captured within step execution callbacks" was incorrect — `StepOutcomeEvent` does not carry routing/CBR/trust metadata. Domain repos (fsitrading) implement `NarrativeSignalStrategy` with domain-specific observer wiring.
**Trade-offs:** Observer receives all step/case outcomes, not just those producing narrative-relevant signals — must filter. But filtering is cheap (predicate on step type or case state).
**Sources:** `StepOutcomeObserver.java` (platform SPI), `CaseOutcomeObserver.java` (platform SPI), issue #241 body (references both interfaces)
**Exploration:** surfaced by review (R1-06)
**Status:** revised (R1-02: corrected signal source assertion, documented platform SPI gap; R1-07: NarrativeSignalStrategy now extends platform observers directly)
