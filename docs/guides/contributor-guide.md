# casehub-blocks -- Contributor Guide

> Internals, architecture, and extension points for platform builders working on casehub-blocks itself.

**GitHub:** [casehubio/blocks](https://github.com/casehubio/blocks)

---

## Module Structure

Single module -- `casehub-blocks` is a flat library, not a multi-module reactor. Single artifact: `casehub-blocks`.

| Path | Contents |
|------|----------|
| `src/main/java/io/casehub/blocks/channel/` | Channel utility blocks -- message meta, context tracking, bounded projection, agent dispatch |
| `src/main/java/io/casehub/blocks/channel/summary/` | Channel summary integration -- `ContentSummariser<Message>` implementations + `SummaryUpdateHook` adapter |
| `src/main/java/io/casehub/blocks/conversation/` | Structured conversation protocol -- projections, fold state, rendering, epistemic common ground, convergence detection |
| `src/main/java/io/casehub/blocks/oversight/` | Oversight gate lifecycle + risk classification -- SPIs, classifier chaining, gate outcomes |
| `src/main/java/io/casehub/blocks/agentic/` | Compositional agentic orchestration -- five SPIs, execution drivers, pattern builders (nine sub-packages) |
| `src/main/java/io/casehub/blocks/routing/` | Trust routing audit types -- compliance records for routing decisions |
| `src/main/java/io/casehub/blocks/routing/agent/` | AI-powered AgentRoutingStrategy implementations -- LLM-reasoned and CBR-evidence agent selection |
| `src/main/java/io/casehub/blocks/summarisation/` | Temporal abstraction framework + content summarisation SPI -- event levels, windowed accumulation, pluggable summarisation |
| `src/main/java/io/casehub/blocks/summarisation/llm/` | LLM-backed `ContentSummariser<T>` -- generic synthesis via `AgentProvider` |
| `src/main/java/io/casehub/blocks/summarisation/observation/` | Observation accumulator -- tiered, demand-driven rendering for LLM agent prompts |
| `src/main/java/io/casehub/blocks/summarisation/observation/affordance/` | Affordance grounding -- per-entity observation rendering for LLM agents |

Tests mirror source layout under `src/test/java/`. Two integration test examples exist: `summarisation/examples/clinical/` (L1-L4 pipeline) and `summarisation/examples/logistics/` (L1-L4 pipeline).

## Internal Architecture

### Oversight Gate Lifecycle

SPIs for gating worker actions pending human approval. Extracted from engine-api and openclaw.

| Class | Role |
|-------|------|
| `ActionRiskClassifier` | Blocking SPI: classifies `PlannedAction` -> `RiskDecision`. Annotate implementations with `@RiskClassifier @ApplicationScoped`. |
| `ReactiveActionRiskClassifier` | Reactive SPI: primary interface called by the engine. |
| `ChainedReactiveActionRiskClassifier` | CDI bean: discovers all `@RiskClassifier`-qualified classifiers, chains them, returns most-restrictive decision. Fail-safe: GateRequired on any exception. |
| `RiskDecision` | Sealed interface (Autonomous, GateRequired). GateRequired carries reason, reversible flag, candidateGroups, expiresIn, scope. |
| `OversightGateService` / `ReactiveOversightGateService` | Blocking/reactive SPIs: `openGate()` -> `GateOutcome`, `fulfill()`. |
| `GateOutcome` | Sealed interface (Autonomous, GatePending). GatePending carries gateId + reason. |

### Agentic Decomposition Strategies

Seven decomposition strategies with increasing sophistication:

| Strategy | Approach |
|----------|----------|
| `IdentityDecomposition` | Pass-through -- wraps single task as-is |
| `StaticDecomposition` | Pre-defined task breakdown via `DecompositionMethod` |
| `ForwardReasoningDecomposition` | SHOP-style forward reasoning -- applies `PrimitiveTask.effect()` to projected state |
| `LlmDecomposition` | Recursive multi-level LLM planning via `maxDepth`. Subtask entries become `CompoundTask` nodes. |
| `HybridDecomposition` | Static-first with LLM fallback and `staticFailureHint` |
| `GoalOrientedDecomposition` | GOAP backward-chaining from goal state |
| `HeuristicDecomposition` | Ranked method selection via pluggable `DecompositionHeuristic<T>` with backtracking |

### Agentic Accountability Listeners

Three listeners for execution audit trails:

| Listener | Sink | Purpose |
|----------|------|---------|
| `EventLogListener` | `EventSink` | Operational audit |
| `LedgerExecutionListener` | `LedgerSink` | Compliance audit |
| `MetricsListener` | OTel `Meter` | Telemetry metrics |

### Routing Agent Internals

`RoutingSupport` is the package-private utility shared by both `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy`. Handles prompt building, response parsing, `AgentProvider` invocation, and trust classification extraction via `TrustFilterOutcome` sealed interface.

Five `RoutingSignalProvider` implementations:
- `PlanCompositionAnalyser` (id: `"plan-composition"`) -- case-level outcomes in multi-step plans
- `CoordinationSignalProvider` (id: `"coordination"`) -- team composition outcomes with adaptation-guided retrieval
- `PredecessorAnalyser` (id: `"predecessor"`) -- immediate predecessor context in historical plan traces
- `DispositionAwareRouting` (id: `"disposition"`) -- personality/disposition match scoring
- CBR outcome weights are domain-tunable via `CbrOutcomeWeights` (step-level), `CbrCaseOutcomeWeights` (case-level), and `CoordinationOutcomeWeights` (coordination case-level) SPIs

### Summarisation Observation Pipeline

Terminal consumer of the summarisation pipeline -- tiered, demand-driven rendering for LLM agent prompts.

- `ObservationAccumulator<E>` -- thread-safe buffer with demand-driven drain and at-most-once delivery on renderer failure
- `TieredObservationRenderer<E>` -- routes to verbatim, grouped, or summarised based on batch size
- `AffordanceRenderer` -- grounded observation rendering: per-entity affordance chains (identity + action + consequence) and typed section assembly

## Trust Routing Architecture

The trust routing system spans four layers -- blocks owns AI-powered routing strategies and compliance audit types.

| Layer | Owner | What it does |
|-------|-------|-------------|
| Score computation | **ledger** | `TrustScoreRoutingPublisher` computes trust scores from ledger entries and publishes them. |
| Policy configuration | **engine-api** | `TrustRoutingPolicyProvider` SPI + `TrustRoutingPolicyKeys` + `TrustRoutingPolicyResolver`. Domain repos implement the provider. |
| Classical strategy execution | **engine** | `TrustWeightedAgentStrategy` (engine-ledger) applies trust scores. `SemanticAgentRoutingStrategy` (engine-ai) adds embedding-based re-ranking. |
| AI-powered strategy execution | **blocks** (routing.agent) | `LlmAgentRoutingStrategy` (LLM reasoning), `CbrAgentRoutingStrategy` (case-based evidence). Both optionally compose with trust classification via `Instance<TrustCandidateClassifier>`. |
| Compliance audit types | **blocks** (routing) | `RoutingDecisionRecord`, `TrustRoutingRequirement`, `RequirementStatus` -- audit trail records. |

Domain repos (aml, devtown, clinical, life, ops) implement `TrustRoutingPolicyProvider` from engine-api -- they configure policy parameters, not compute scores or execute routing.

## Consolidation Epic

Epic #28 tracks extraction of shared patterns from domain repos into blocks.

| # | Title | Status | Destination |
|---|-------|--------|-------------|
| #17 | Trust routing YAML | Done | blocks |
| #22 | Debate channel infrastructure | Done | blocks |
| #23 | Oversight gate lifecycle + risk classification | Done | blocks |
| #24 | Universal pluggable routing strategy | Moved -> engine#634 | engine |
| #30 | AI routing strategy impls (trust, LLM, CBR) | Done | blocks |
| #25 | Worker data coordination (DataExchange/DataChannel) | Moved -> engine#633 | engine |
| #27 | Layered event summarisation | Done | blocks |

## Depended On By

| Repo | What it uses |
|------|-------------|
| casehub-drafthouse | Channel + conversation blocks -- DebateProtocol delegates to `ConversationProtocol`, DebateChannelProjection extends `ConversationProjection`, `ChannelAgentDispatcher`, `BoundedProjectionDecorator`, `ContextTracker` |
| casehub-engine | Oversight: `GateOutcome`, `OversightGateService`, `ReactiveActionRiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-openclaw | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `GateOutcome` (concrete OversightGateService impl) |
| casehub-aml | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver`. Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision` |
| casehub-devtown | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`. Oversight: `ActionRiskClassifier`, `RiskClassifier` |
| casehub-life | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`. Oversight: `ActionRiskClassifier`, `RiskClassifier` |
| casehub-soc | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision` |
| casehub-clinical | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision` |
| casehub-iot | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision` |
| casehub-quarkmind | Summarisation: `SummarisationRunner`, `EventStreamBus`, `EventAccumulator`, `WindowPolicy`, `Summariser`, `EventLevel`, `LevelEvent` |

## Design Documents

- [Agentic orchestration research](https://raw.githubusercontent.com/casehubio/blocks/main/docs/agentic-orchestration-research.md)
- [Execution backend architecture](https://raw.githubusercontent.com/casehubio/blocks/main/docs/execution-backend-architecture.md)
- [Unified execution model](https://raw.githubusercontent.com/casehubio/blocks/main/docs/unified-execution-model.md)
- [Conversation projections vs chat logs](https://raw.githubusercontent.com/casehubio/blocks/main/docs/conversation-projections-vs-chat-logs.md)
