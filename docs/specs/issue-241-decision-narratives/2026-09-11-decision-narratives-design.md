# Decision Narratives — Design Spec

**Issue:** casehubio/blocks#241
**Date:** 2026-09-11
**Branch:** issue-241-decision-narratives

## Overview

Human-readable explanations of agent decisions, built as a thin consumer of
the existing summarisation pipeline. Platform signals (routing decisions, CBR
retrievals, trust scores, deliberation outcomes, step outcomes) flow through
a two-level pipeline that produces structured `DecisionNarrative` records.

The existing narrative infrastructure (`NarrativeFragment`, `NarrativeOrchestrator`,
`NarrativeSynthesiser`) handles first-person identity narratives synthesised from
agent reflections. Decision narratives are third-person operational explanations
of what the platform decided and why. The two systems are architecturally separate:
different input types, different output types, different consumers. They share only
the underlying summarisation pipeline primitives.

### Architectural approach

Rather than extending the `NarrativeFragment` sealed hierarchy with decision-specific
subtypes, decision narratives compose the generic summarisation pipeline:
`KeyedSummarisationRunner`, `EventStreamBus`, `ContentSummariser`, `Summariser`.
This validates the pipeline's genericity (the same primitives that power clinical
and logistics examples now power narrative generation) and keeps the identity
narrative stack (`NarrativeFragment`, `NarrativeOrchestrator`, `NarrativePromptSection`,
`SocialAvatarCognition`) undisturbed.

### Scope changes from issue #241

Issue #241 originally proposed extending `NarrativeFragment` with decision-specific
subtypes (`RoutingRationale`, `CbrEvidence`, `TrustContext`, `DeliberationSummary`,
`StepOutcomeSummary`), a `DecisionNarrativeOrchestrator`, and a `NarrativeStateSchema`
extension. This spec rejects all three:

- **Extending `NarrativeFragment`** conflates identity narratives (first-person
  reflective) with operational explanations (third-person decisional), forces every
  `NarrativeFragment` consumer to handle unwanted variants, and breaks the sealed
  hierarchy's semantic boundary.
- **`DecisionNarrativeOrchestrator`** is replaced by composition of existing pipeline
  primitives (`KeyedSummarisationRunner`, `EventStreamBus`, `ContentSummariser`),
  validating the pipeline's genericity rather than building a parallel orchestrator.
- **`NarrativeStateSchema`** is unnecessary — decision narrative state is managed
  by the pipeline's per-key `StatefulSummariser` support.

Issue #241's body will be updated at implementation time to reflect the actual approach.

## Architecture

```
Platform signals                           Consumer
─────────────────                          ────────

StepOutcomeObserver ─┐
CaseOutcomeObserver ─┤
                     ▼
         NarrativeSignalStrategy
              (domain SPI)
                     │
                     ▼ LevelEvent<DecisionSignal>
         ┌───────────────────────┐
         │  EventStreamBus<DS>   │  L0 — raw signals
         └───────┬───────────────┘
                 │
    ┌────────────▼────────────────┐
    │  KeyedSummarisationRunner   │  L1 — per-step heuristic
    │  key: caseId + stepName     │       accumulation
    │  summariser: heuristic      │
    │  (no LLM, microseconds)     │
    └────────────┬────────────────┘
                 │ LevelEvent<StepDecisionSummary>
    ┌────────────▼────────────────┐
    │  EventStreamBus<SDS>        │  L1→L2 bus
    └────────────┬────────────────┘
                 │
    ┌────────────▼────────────────┐
    │  KeyedSummarisationRunner   │  L2 — per-case LLM synthesis
    │  key: caseId                │
    │  summariser: LLM-backed     │
    │  (ContentSummariser via     │
    │   asSummariser())           │
    └────────────┬────────────────┘
                 │ LevelEvent<DecisionNarrative>
    ┌────────────▼────────────────┐
    │  EventStreamBus<DN>         │  L2 output bus
    └────────────┬────────────────┘
                 │
                 ▼
      Timeline / Ops Centre / REST
```

L1 groups raw signals by `caseId:stepName`, extracts structured key facts
(heuristic — no LLM cost), and emits `StepDecisionSummary` records.

L2 groups step summaries by `caseId`, passes them to an LLM-backed
`ContentSummariser` that produces a human-readable `DecisionNarrative`
with `@Nullable` previous state for incremental updates as steps complete.

### Signal source availability

The `DecisionSignal` sealed hierarchy defines the target state for all five
signal types. However, platform observer SPIs do not currently expose all the
data needed to populate every variant:

| Signal type | Platform source | Batch |
|---|---|---|
| `StepOutcome` | `StepOutcomeObserver.onStepOutcome(StepOutcomeEvent)` — all fields directly available | 1–2 |
| `RoutingDecision` | Requires new `RoutingDecisionObserver` SPI — `RoutingResult.Selected` carries `RoutingSelection` (strategyId, score, alternatives) but this is not surfaced to any observer | Future |
| `CbrRetrieval` | Partial via `AgentRoutingContext.experiences()` — `RetrievedExperience` carries similarityScore and outcome. Full observer SPI needed to decouple from routing | Future |
| `TrustAssessment` | Requires new observer SPI — trust scores are computed inside routing strategies (`TrustWeightedAgentStrategy`) but not surfaced to observers | Future |
| `DeliberationOutcome` | Requires deliberation infrastructure — no `Deliberation` class exists in the platform | Future |

Batch 1–2 implements the full pipeline (L1 heuristic → L2 LLM synthesis)
end-to-end with `StepOutcome` signals only. The sealed hierarchy ensures
compile-time enforcement when new signal types are added. Platform observer
SPI issues are tracked separately (see §What's deferred).

#### Platform field mappings

`NarrativeSignalStrategy` implementations map platform events to `DecisionSignal`:

| `DecisionSignal` field | Platform source | Mapping |
|---|---|---|
| `caseId` (String) | `StepOutcomeEvent.caseId()` (UUID) | `UUID.toString()` |
| `stepName` (String) | `StepOutcomeEvent.bindingName()` (String) | Direct — `bindingName` identifies the case definition binding that dispatched the step |
| `status` (String, `StepOutcome`) | `StepOutcomeEvent.outcome()` (RoutingOutcome) | `RoutingOutcome.name()` |

## KeyedSummarisationRunner — StatefulSummariser support

**Prerequisite** (D7). `KeyedSummarisationRunner` currently always calls
`summariser.summarise(batch)` (the stateless `Summariser` path), even when
the summariser is a `StatefulSummariser`. L2 needs per-key state so the LLM
receives the previous `DecisionNarrative` for incremental updates.

### Change

Mirror the pattern from `SummarisationRunner.invokeSummariser()` (line 112):

```java
public class KeyedSummarisationRunner<K, IN, OUT> {

    // new field
    private final ConcurrentHashMap<K, Object> keyState = new ConcurrentHashMap<>();

    // in tick() and flush(), replace the direct summariser.summarise(batch) call:
    private CompletionStage<Void> invokeSummariser(List<LevelEvent<IN>> batch, K key, long now) {
        String tenancyId = batch.isEmpty() ? null : batch.get(0).tenancyId();
        if (summariser instanceof StatefulSummariser<IN, OUT, ?> stateful) {
            @SuppressWarnings("unchecked")
            var typedStateful = (StatefulSummariser<IN, OUT, Object>) stateful;
            Object prevState = keyState.get(key);
            return typedStateful.summarise(batch, prevState).thenAccept(result -> {
                if (result.newState() != null) {
                    keyState.put(key, result.newState());
                }
                for (var payload : result.outputs()) {
                    outputBus.publish(new LevelEvent<>(payload, now, outputLevel, tenancyId));
                }
            });
        }
        return summariser.summarise(batch).thenAccept(results -> {
            for (var payload : results) {
                outputBus.publish(new LevelEvent<>(payload, now, outputLevel, tenancyId));
            }
        });
    }
}
```

The `keyState` map uses `K` as key (not `tenancyId`), so state is per-group.
This is correct: L2 groups by `caseId`, so state accumulates per case.

### State eviction

`SummarisationRunner.partitionState` is keyed by `tenancyId` (bounded
cardinality). `KeyedSummarisationRunner.keyState` is keyed by the group key,
which for L2 is `caseId` (unbounded cardinality). Without eviction, `keyState`
grows monotonically — a memory leak for long-running systems.

Add an explicit eviction method:

```java
public void evictState(K key) {
    keyState.remove(key);
}
```

The pipeline calls `l2.evictState(caseId)` when `CaseOutcomeEvent` fires —
tying state lifetime to case lifetime. After eviction, any subsequent step
summaries for the same case start fresh (acceptable — the case has closed).

### Backward compatibility

Existing consumers pass a plain `Summariser` — the `instanceof` check falls
through to the stateless path. No behaviour change for existing code.

### Tests

- `StatefulSummariserIntegrationTest` — verify per-key state propagation
  across multiple `tick()` cycles with a `ContentSummariser.asSummariser()`
- Existing `KeyedSummarisationRunnerTest` — must pass unchanged

## DecisionSignal sealed hierarchy

Package: `io.casehub.blocks.summarisation.narrative`

```java
public sealed interface DecisionSignal
        permits RoutingDecision, CbrRetrieval, TrustAssessment,
                DeliberationOutcome, StepOutcome {

    String caseId();

    String stepName();

    Instant timestamp();
}
```

All variants carry `caseId` and `stepName` — the L1 grouping key.

### RoutingDecision

```java
public record RoutingDecision(
        String caseId,
        String stepName,
        Instant timestamp,
        String selectedAgentId,
        String strategyId,
        double score,
        List<String> candidateIds,
        @Nullable String reason
) implements DecisionSignal {
    public RoutingDecision {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(selectedAgentId);
        Objects.requireNonNull(strategyId);
        if (score < 0.0 || score > 1.0)
            throw new IllegalArgumentException("score must be in [0, 1]");
        candidateIds = List.copyOf(candidateIds);
    }
}
```

### CbrRetrieval

```java
public record CbrRetrieval(
        String caseId,
        String stepName,
        Instant timestamp,
        int retrievedCount,
        double topSimilarity,
        @Nullable String topCaseOutcome,
        @Nullable String domain
) implements DecisionSignal {
    public CbrRetrieval {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        if (retrievedCount < 0)
            throw new IllegalArgumentException("retrievedCount must be >= 0");
        if (topSimilarity < 0.0 || topSimilarity > 1.0)
            throw new IllegalArgumentException("topSimilarity must be in [0, 1]");
    }
}
```

### TrustAssessment

```java
public record TrustAssessment(
        String caseId,
        String stepName,
        Instant timestamp,
        String agentId,
        double trustScore,
        double threshold,
        boolean passed
) implements DecisionSignal {
    public TrustAssessment {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(agentId);
        if (trustScore < 0.0 || trustScore > 1.0)
            throw new IllegalArgumentException("trustScore must be in [0, 1]");
        if (threshold < 0.0 || threshold > 1.0)
            throw new IllegalArgumentException("threshold must be in [0, 1]");
    }
}
```

### DeliberationOutcome

```java
public record DeliberationOutcome(
        String caseId,
        String stepName,
        Instant timestamp,
        String outcome,
        int rounds,
        @Nullable String convergenceState,
        List<String> participantIds
) implements DecisionSignal {
    public DeliberationOutcome {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(outcome);
        if (rounds < 0)
            throw new IllegalArgumentException("rounds must be >= 0");
        participantIds = List.copyOf(participantIds);
    }
}
```

### StepOutcome

```java
public record StepOutcome(
        String caseId,
        String stepName,
        Instant timestamp,
        String status,
        @Nullable String workerId,
        @Nullable String errorMessage,
        Duration elapsed
) implements DecisionSignal {
    public StepOutcome {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(status);
        Objects.requireNonNull(elapsed);
    }
}
```

## StepDecisionSummary — L1→L2 intermediate type

The L1 heuristic summariser produces this intermediate record, flattening
sealed `DecisionSignal` variants into domain-agnostic `SignalDigest` records.

```java
public record StepDecisionSummary(
        String caseId,
        String stepName,
        List<SignalDigest> signals,
        Instant from,
        Instant to
) {
    public StepDecisionSummary {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(stepName);
        signals = List.copyOf(signals);
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
    }
}
```

```java
public record SignalDigest(
        String signalType,
        String summary,
        Map<String, String> keyFacts,
        double confidence
) {
    public SignalDigest {
        Objects.requireNonNull(signalType);
        Objects.requireNonNull(summary);
        keyFacts = Map.copyOf(keyFacts);
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
    }
}
```

`keyFacts` is intentionally stringly-typed — it is LLM prompt input, not
programmatic API. `confidence` is a signal-type-specific quality metric, NOT
a calibrated probability — its semantics vary by signal type (routing score for
`RoutingDecision`, similarity score for `CbrRetrieval`, trust score for
`TrustAssessment`, consensus indicator for `DeliberationOutcome`, completion
indicator for `StepOutcome`). The L2 LLM summariser receives these as structured
input and produces its own coherent confidence assessment. Example key-value pairs:

| Signal type | Example keyFacts entries |
|---|---|
| `RoutingDecision` | `"selected"→"analyst-3"`, `"strategy"→"cbr"`, `"score"→"0.87"`, `"candidates"→"3"` |
| `CbrRetrieval` | `"retrieved"→"5"`, `"topSimilarity"→"0.92"`, `"topOutcome"→"COMPLETED"` |
| `TrustAssessment` | `"agent"→"analyst-3"`, `"trust"→"0.85"`, `"threshold"→"0.70"`, `"passed"→"true"` |
| `DeliberationOutcome` | `"outcome"→"AGREED"`, `"rounds"→"3"`, `"convergence"→"CONSENSUS"` |
| `StepOutcome` | `"status"→"COMPLETED"`, `"worker"→"analyst-3"`, `"elapsed"→"PT2.3S"` |

## DecisionNarrative — pipeline output type

```java
public record DecisionNarrative(
        String caseId,
        List<String> stepNames,
        String explanation,
        List<String> evidenceSources,
        double confidence,
        Instant producedAt
) {
    public DecisionNarrative {
        Objects.requireNonNull(caseId);
        stepNames = List.copyOf(stepNames);
        Objects.requireNonNull(explanation);
        evidenceSources = List.copyOf(evidenceSources);
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        Objects.requireNonNull(producedAt);
    }
}
```

`stepNames` records which steps were synthesized into this case-level narrative —
L2 groups by `caseId` and accumulates step summaries, so the output is case-level,
not step-level. `explanation` is the LLM-produced human-readable narrative.
`evidenceSources` lists the signal types that contributed (e.g., `["RoutingDecision",
"CbrRetrieval", "TrustAssessment"]`). `confidence` is the LLM's assessment of
overall decision quality in the LLM path; in the template fallback, it is the
minimum across contributing `SignalDigest` entries — a rough heuristic, not a
calibrated probability.

## L1 Heuristic Summariser

`DecisionSignalSummariser` implements `Summariser<DecisionSignal, StepDecisionSummary>`.
Pure Java, no LLM, synchronous via `Summariser.ofSync()`.

```java
public class DecisionSignalSummariser
        implements Summariser.SyncSummariser<DecisionSignal, StepDecisionSummary> {

    @Override
    public List<StepDecisionSummary> summarise(List<LevelEvent<DecisionSignal>> batch) {
        if (batch.isEmpty()) return List.of();

        var first = batch.get(0).payload();
        var digests = batch.stream()
                .map(e -> toDigest(e.payload()))
                .toList();

        return List.of(new StepDecisionSummary(
                first.caseId(),
                first.stepName(),
                digests,
                batch.get(0).payload().timestamp(),
                batch.get(batch.size() - 1).payload().timestamp()));
    }

    static SignalDigest toDigest(DecisionSignal signal) {
        return switch (signal) {
            case RoutingDecision r -> new SignalDigest(
                    "RoutingDecision",
                    "Selected " + r.selectedAgentId() + " via " + r.strategyId(),
                    Map.of("selected", r.selectedAgentId(),
                           "strategy", r.strategyId(),
                           "score", String.valueOf(r.score()),
                           "candidates", String.valueOf(r.candidateIds().size())),
                    r.score());
            case CbrRetrieval c -> new SignalDigest(
                    "CbrRetrieval",
                    "Retrieved " + c.retrievedCount() + " cases, top similarity " + c.topSimilarity(),
                    Map.of("retrieved", String.valueOf(c.retrievedCount()),
                           "topSimilarity", String.valueOf(c.topSimilarity()),
                           "topOutcome", c.topCaseOutcome() != null ? c.topCaseOutcome() : "unknown"),
                    c.topSimilarity());
            case TrustAssessment t -> new SignalDigest(
                    "TrustAssessment",
                    t.agentId() + " trust " + t.trustScore() + (t.passed() ? " (passed)" : " (failed)"),
                    Map.of("agent", t.agentId(),
                           "trust", String.valueOf(t.trustScore()),
                           "threshold", String.valueOf(t.threshold()),
                           "passed", String.valueOf(t.passed())),
                    t.trustScore());
            case DeliberationOutcome d -> new SignalDigest(
                    "DeliberationOutcome",
                    d.outcome() + " after " + d.rounds() + " rounds",
                    Map.of("outcome", d.outcome(),
                           "rounds", String.valueOf(d.rounds()),
                           "convergence", d.convergenceState() != null ? d.convergenceState() : "unknown"),
                    d.convergenceState() != null && d.convergenceState().equals("CONSENSUS") ? 1.0 : 0.5);
            case StepOutcome s -> new SignalDigest(
                    "StepOutcome",
                    s.stepName() + " " + s.status() + " in " + s.elapsed(),
                    Map.of("status", s.status(),
                           "worker", s.workerId() != null ? s.workerId() : "unknown",
                           "elapsed", s.elapsed().toString()),
                    s.status().equals("COMPLETED") ? 1.0 : 0.3);
        };
    }
}
```

The sealed `switch` ensures compile-time exhaustiveness — new `DecisionSignal`
permits force an update here.

## L2 LLM Summariser

`DecisionNarrativeSummariser` implements `ContentSummariser<StepDecisionSummary,
DecisionNarrative>`. LLM-backed via `AgentProvider`. Uses `asSummariser()` to
bridge to `StatefulSummariser` for the `KeyedSummarisationRunner`.

```java
public class DecisionNarrativeSummariser
        implements ContentSummariser<StepDecisionSummary, DecisionNarrative> {

    static final String SYSTEM_PROMPT = """
            You are generating a concise decision narrative explaining why an \
            AI agent made specific decisions during case execution. Given the \
            step-level decision summaries and (optionally) the previous narrative, \
            produce a JSON response with:

            1. explanation — a 2-4 sentence human-readable explanation of the \
            decisions made. Focus on WHY: what evidence led to the routing choice, \
            what historical cases informed the approach, what trust levels influenced \
            agent selection.

            2. evidenceSources — array of signal types that contributed (e.g., \
            "RoutingDecision", "CbrRetrieval").

            3. confidence — a number [0.0, 1.0] reflecting overall decision confidence.

            If a previous narrative is provided, incorporate new steps into the \
            existing explanation rather than starting from scratch. Preserve prior \
            context and extend it.

            Respond with JSON only. No explanation outside the JSON.""";

    private final AgentProvider agentProvider;

    // CDI constructor
    @Inject
    public DecisionNarrativeSummariser(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public CompletionStage<DecisionNarrative> summarise(
            List<StepDecisionSummary> items,
            @Nullable DecisionNarrative previous) {
        // assemble user prompt from items + previous
        // invoke agentProvider
        // parse JSON response into DecisionNarrative
        // ...
    }
}
```

The user prompt assembles step summaries as numbered sections:

```
## Previous narrative
(explanation from previous DecisionNarrative, or "None — first synthesis")

## New step summaries
1. Step "route-analyst" (2026-09-11T10:00:00Z → 2026-09-11T10:00:01Z)
   - RoutingDecision: Selected analyst-3 via cbr [score=0.87, candidates=3]
   - CbrRetrieval: Retrieved 5 cases, top similarity 0.92 [topOutcome=COMPLETED]
   - TrustAssessment: analyst-3 trust 0.85 (passed) [threshold=0.70]

2. Step "execute-trade" (2026-09-11T10:00:02Z → 2026-09-11T10:00:04Z)
   - StepOutcome: execute-trade COMPLETED in PT2.3S [worker=analyst-3]
```

### Template fallback

The template fallback activates when the LLM invocation returns empty content
or throws an exception. `NoOpAgentProvider` (the CDI default when no agent
backend is on the classpath) returns `Multi.createFrom().empty()`, producing
empty text — the summariser detects this and falls back to template generation.
This makes the fallback an automatic degradation mechanism, not a configuration
mode:

```java
private DecisionNarrative templateFallback(List<StepDecisionSummary> items,
                                            @Nullable DecisionNarrative previous) {
    var sb = new StringBuilder();
    for (var step : items) {
        sb.append("Step ").append(step.stepName()).append(": ");
        for (var digest : step.signals()) {
            sb.append(digest.summary()).append(". ");
        }
    }
    // ...produce DecisionNarrative from template
}
```

## NarrativeSignalStrategy — observer-driven SPI

```java
public interface NarrativeSignalStrategy extends StepOutcomeObserver, CaseOutcomeObserver {
    // inherits onStepOutcome(StepOutcomeEvent) from StepOutcomeObserver
    // inherits onOutcome(CaseOutcomeEvent) from CaseOutcomeObserver
}
```

`NarrativeSignalStrategy` extends both platform observer interfaces directly.
A CDI bean implementing `NarrativeSignalStrategy` is automatically discovered
by the engine as both a `StepOutcomeObserver` and `CaseOutcomeObserver` —
no separate observer registration needed.

Domain repos implement this SPI. The blocks-level abstract base class
wires it to the pipeline and handles state eviction on case close:

```java
public abstract class AbstractNarrativeSignalStrategy implements NarrativeSignalStrategy {

    private final EventStreamBus<DecisionSignal> signalBus;
    private final DecisionNarrativePipeline pipeline;

    protected AbstractNarrativeSignalStrategy(EventStreamBus<DecisionSignal> signalBus,
                                               DecisionNarrativePipeline pipeline) {
        this.signalBus = signalBus;
        this.pipeline = pipeline;
    }

    protected void emit(DecisionSignal signal) {
        signalBus.publish(new LevelEvent<>(
                signal, signal.timestamp().toEpochMilli(),
                new EventLevel("decision-signal", 0),
                extractTenancyId(signal)));
    }

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        pipeline.evictCaseState(event.caseId().toString());
    }

    protected abstract @Nullable String extractTenancyId(DecisionSignal signal);
}
```

The domain implementation (e.g., `FsiNarrativeSignalStrategy` in fsitrading)
extends this base, converts platform events to `DecisionSignal` variants,
and calls `emit()`. The `onOutcome()` default handles L2 state eviction on
case close. Domain implementations can override to emit additional signals
before calling `super.onOutcome(event)`.

## Pipeline wiring

The full pipeline is assembled by a `DecisionNarrativePipeline` factory:

```java
@ApplicationScoped
public class DecisionNarrativePipeline {

    static final EventLevel L0_SIGNALS = new EventLevel("decision-signals", 0);
    static final EventLevel L1_STEPS = new EventLevel("step-summaries", 1);
    static final EventLevel L2_NARRATIVES = new EventLevel("decision-narratives", 2);

    private final EventStreamBus<DecisionSignal> signalBus;
    private final EventStreamBus<StepDecisionSummary> stepBus;
    private final EventStreamBus<DecisionNarrative> narrativeBus;
    private final KeyedSummarisationRunner<String, DecisionSignal, StepDecisionSummary> l1;
    private final KeyedSummarisationRunner<String, StepDecisionSummary, DecisionNarrative> l2;
    private PipelineTickScheduler scheduler;

    @Inject
    public DecisionNarrativePipeline(AgentProvider agentProvider) {
        this.signalBus = new EventStreamBus<>();
        this.stepBus = new EventStreamBus<>();
        this.narrativeBus = new EventStreamBus<>();

        this.l1 = new KeyedSummarisationRunner<>(
                e -> e.payload().caseId() + ":" + e.payload().stepName(),
                group -> group.stream().anyMatch(
                        e -> e.payload() instanceof StepOutcome),
                30_000L,
                Summariser.ofSync(new DecisionSignalSummariser()),
                stepBus, L1_STEPS);

        this.l2 = new KeyedSummarisationRunner<>(
                e -> e.payload().caseId(),
                group -> group.size() >= 1,
                60_000L,
                new DecisionNarrativeSummariser(agentProvider).asSummariser(),
                narrativeBus, L2_NARRATIVES);

        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    @PostConstruct
    void start() {
        scheduler = new PipelineTickScheduler(List.of(l1, l2), 5_000L);
        scheduler.start();
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) scheduler.stop();
    }

    public EventStreamBus<DecisionSignal> signalBus() { return signalBus; }
    public EventStreamBus<DecisionNarrative> narrativeBus() { return narrativeBus; }

    public void evictCaseState(String caseId) {
        l2.evictState(caseId);
    }
}
```

### L1 completion test

L1 groups by `caseId:stepName`. A group is complete when it contains a
`StepOutcome` signal (the terminal event for a step). Stale timeout (30s)
handles steps that don't produce a `StepOutcome` (failure paths).

### L2 completion test

L2 groups by `caseId`. Completion fires immediately on each step summary
arrival (`group.size() >= 1`) to produce incremental narrative updates.
Stale timeout (60s) handles the final case-level synthesis. The
`ContentSummariser.asSummariser()` bridge provides per-key state: each
`tick()` receives the previous `DecisionNarrative` for that case.

**Cost tradeoff:** Per-step firing means a case with N steps produces up to
N LLM calls, each incorporating the previous narrative for incremental update.
This is intentional — it enables real-time Timeline display where the narrative
updates as each step completes. The `StatefulSummariser` pattern means each
call is incremental (extending the prior narrative), not full reconstruction.
Steps completing faster than the tick interval (5s) naturally batch into a
single LLM call. Deployments that prefer lower cost over real-time updates
can adjust `completionTest` (e.g., `group.size() >= 3` or time-based gating).

## YAML example — trading decision pipeline

Self-contained test in `blocks/src/test/java/io/casehub/blocks/summarisation/
examples/decision/DecisionNarrativePipelineTest.java`.

Defines a local `TradingSignal` sealed hierarchy (no dependency on production
`DecisionSignal`):

```java
sealed interface TradingSignal {
    String tradeId();
    String step();
    Instant timestamp();
}
record AnalystRouting(String tradeId, String step, Instant timestamp,
                      String selectedAnalyst, double score) implements TradingSignal {}
record HistoricalMatch(String tradeId, String step, Instant timestamp,
                       int matchCount, double topSimilarity) implements TradingSignal {}
record TradeResult(String tradeId, String step, Instant timestamp,
                   String outcome, Duration elapsed) implements TradingSignal {}
```

The test wires a two-level pipeline with a heuristic L1 summariser and
a template L2 summariser (no LLM dependency in tests), then feeds 3 trades
through and asserts:

1. L1 produces one `StepDecisionSummary` per trade-step
2. L2 produces one narrative per trade
3. Multi-tenant signals don't cross-contaminate
4. Stale timeout fires for incomplete groups

## Package structure

```
summarisation-api/src/main/java/io/casehub/blocks/summarisation/narrative/
├── DecisionSignal.java          (sealed interface)
├── RoutingDecision.java         (record)
├── CbrRetrieval.java            (record)
├── TrustAssessment.java         (record)
├── DeliberationOutcome.java     (record)
├── StepOutcome.java             (record)
├── StepDecisionSummary.java     (record)
├── SignalDigest.java            (record)
├── DecisionNarrative.java       (record)
├── DecisionSignalSummariser.java (L1 heuristic)
├── DecisionNarrativeSummariser.java (L2 LLM-backed)
└── DecisionNarrativePipeline.java   (factory/wiring + CDI lifecycle)

blocks/src/main/java/io/casehub/blocks/summarisation/narrative/
├── NarrativeSignalStrategy.java     (SPI — extends StepOutcomeObserver + CaseOutcomeObserver)
└── AbstractNarrativeSignalStrategy.java (base class)

blocks/src/test/java/io/casehub/blocks/summarisation/narrative/
├── DecisionSignalSummariserTest.java
├── DecisionNarrativeSummariserTest.java
├── DecisionNarrativePipelineTest.java

blocks/src/test/java/io/casehub/blocks/summarisation/examples/decision/
└── DecisionNarrativePipelineTest.java  (fsitrading-inspired example)

summarisation-api/src/main/java/io/casehub/blocks/summarisation/
└── KeyedSummarisationRunner.java       (enhanced — StatefulSummariser + eviction)

summarisation-api/src/test/java/io/casehub/blocks/summarisation/
└── KeyedSummarisationRunnerStatefulTest.java
```

Pipeline types, data records, and summarisers live in `summarisation-api` —
following the existing pattern where `LlmContentSummariser` (which also
uses `AgentProvider`) lives in `summarisation-api`. The `summarisation-api`
module already depends on `platform-agent-api`.

`NarrativeSignalStrategy` and `AbstractNarrativeSignalStrategy` live in the
`blocks` module because they extend `StepOutcomeObserver` and
`CaseOutcomeObserver` from `casehub-engine-api` — a dependency that
`summarisation-api` does not and should not carry. The `KeyedSummarisationRunner`
enhancement lives in `summarisation-api` — it is a pure-Java change with no new
dependencies.

## What's deferred

Three summarisation pipeline SPIs originally planned for #241 were deferred
to #259 (identity narrative retrofit) during decision review:

| SPI | Why deferred |
|---|---|
| `StateStore<S>` (D1) | Decision narratives use in-memory state (re-derivable from signals). Persistent state is needed by identity narratives (`NarrativeMemory`). |
| `EmissionPolicy<IN, S>` (D2) | Decision narratives use `KeyedSummarisationRunner`'s existing `completionTest` + `staleTimeout`. The motivating use case (`NarrativeSynthesisGate` — count + novelty + quiet period) is an identity narrative concern. |
| `OutputProcessor<OUT, S>` (D3) | Decision narratives are naturally bounded (finite signals per step/case). The motivating use case (`pruneEpisodes`/`pruneThemes`) is an identity narrative concern. |

These SPIs will be introduced and validated against their actual consumer
in #259.

Four `DecisionSignal` variants require platform observer SPIs that do not
yet exist. These are tracked as separate issues:

| Signal type | Required platform SPI | Issue |
|---|---|---|
| `RoutingDecision` | `RoutingDecisionObserver` — fires after `AgentRoutingStrategy.select()` with `RoutingResult.Selected` including `RoutingSelection` (strategyId, score, alternatives) | TBD |
| `CbrRetrieval` | `CbrRetrievalObserver` — fires after CBR retrieval with `List<RetrievedExperience>` including similarity scores and outcomes | TBD |
| `TrustAssessment` | `TrustAssessmentObserver` — fires after trust scoring during routing with agent trust score, threshold, and pass/fail | TBD |
| `DeliberationOutcome` | Deliberation infrastructure — no `Deliberation` class exists in the platform | TBD |

These issues will be filed at implementation time when Batch 1-2 proves the
pipeline end-to-end with `StepOutcome` signals.

## Cross-repo scope

| Repo | What | Batch |
|---|---|---|
| blocks | Pipeline enhancement + narrative types + example | 1–2 |
| blocks-ui | `narrative-timeline` component | 3 |
| fsitrading | `FsiNarrativeSignalStrategy` + Ops Centre wiring | 4 |

## References

- `KeyedSummarisationRunner.java` — grouping runner to enhance (lines 75-98, tick/flush)
- `SummarisationRunner.java:112-132` — StatefulSummariser pattern to replicate
- `ContentSummariser.java` — `asSummariser()` bridge
- `NarrativeFragment.java` — existing sealed hierarchy (NOT extended)
- `NarrativeSynthesiser.java` — existing identity narrative synthesiser (NOT modified)
- `ClinicalPipelineTest.java` — multi-level pipeline precedent
- `LogisticsPipelineTest.java` — second pipeline example precedent
- GE-20260825-ba18b3 — polymorphic sealed hierarchy serialization to CBR
- casehubio/blocks#259 — identity narrative retrofit (deferred SPIs)
- decisions.md — D1–D10 architectural decisions
