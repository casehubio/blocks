# casehub-blocks -- Contributor Guide

> Internals, architecture, and extension points for platform builders working on casehub-blocks itself.

**GitHub:** [casehubio/blocks](https://github.com/casehubio/blocks)

---

## Module Structure

Single module -- `casehub-blocks` is a flat library, not a multi-module reactor. Single artifact: `casehub-blocks`.

| Path | Contents |
|------|----------|
| `src/main/java/io/casehub/blocks/channel/` | Channel utility blocks -- message meta, context tracking, bounded projection, agent dispatch |
| `src/main/java/io/casehub/blocks/channel/summary/` | Channel summary integration -- `ContentSummariser<Message>` wiring + `SummaryUpdateHook` adapter |
| `src/main/java/io/casehub/blocks/conversation/` | Structured conversation protocol -- projections, fold state, rendering, epistemic common ground, convergence detection (27 types) |
| `src/main/java/io/casehub/blocks/agentic/` | Compositional agentic orchestration -- five SPIs, execution drivers, pattern builders (ten sub-packages) |
| `src/main/java/io/casehub/blocks/agentic/activation/` | Activation SPI: `ActivationRule`, `ActivationContext`, `Activation` factory, implementations |
| `src/main/java/io/casehub/blocks/agentic/aggregation/` | Aggregation SPI: `AggregationStrategy`, `AggregationContext`, `AggregationResult`, `Aggregation` factory, implementations |
| `src/main/java/io/casehub/blocks/agentic/decomposition/` | Decomposition: `Decomposition`/`Tasks` factories, strategies, heuristics, task types (21 files) |
| `src/main/java/io/casehub/blocks/agentic/listener/` | Accountability listeners: `EventLogListener`, `LedgerExecutionListener`, `MetricsListener`, `OrchestrationEventType` |
| `src/main/java/io/casehub/blocks/agentic/model/` | Execution model: drivers, invokers, backends, state machine (11 files) |
| `src/main/java/io/casehub/blocks/agentic/pattern/` | Pattern DSL: `Patterns` entry point, 8 builders + `AbstractPatternBuilder` base class |
| `src/main/java/io/casehub/blocks/agentic/plan/` | `ExecutionPlan<T>` -- DAG record with cycle validation |
| `src/main/java/io/casehub/blocks/agentic/routing/` | Routing SPI: `RoutingStrategy`, `RoutingDecision`, `Routing` factory, 4 implementations + `StageGate`/`StageAwareCandidateSupplier` |
| `src/main/java/io/casehub/blocks/agentic/termination/` | Termination SPI: `TerminationCondition`, `TerminationDecision`, `Termination` factory, 4 implementations |
| `src/main/java/io/casehub/blocks/routing/` | Trust routing audit types -- compliance records for routing decisions |
| `src/main/java/io/casehub/blocks/routing/agent/` | AI-powered AgentRoutingStrategy implementations -- LLM-reasoned and CBR-evidence agent selection (15 files) |
| `src/main/java/io/casehub/blocks/summarisation/` | Temporal abstraction framework + content summarisation SPI -- event levels, windowed accumulation, pluggable summarisation (14 files) |
| `src/main/java/io/casehub/blocks/summarisation/llm/` | LLM-backed `ContentSummariser<T>` -- generic synthesis via `AgentProvider` |
| `src/main/java/io/casehub/blocks/summarisation/observation/` | Observation accumulator -- tiered, demand-driven rendering for LLM agent prompts (4 files) |
| `src/main/java/io/casehub/blocks/summarisation/observation/affordance/` | Affordance grounding -- per-entity observation rendering for LLM agents (5 files) |

Tests mirror source layout under `src/test/java/`. Integration test examples exist in:
- `summarisation/examples/clinical/` -- L1-L4 clinical pipeline (vital readings -> care phases -> narratives)
- `summarisation/examples/logistics/` -- L1-L4 logistics pipeline (package scans -> anomalies -> hub phases -> narratives)
- `summarisation/examples/channel/` -- tiered channel summary end-to-end
- `summarisation/examples/multiagent/` -- multi-agent observation pipeline with partitioned observation service
- `agentic/decomposition/examples/incident/` -- HTN hybrid decomposition for incident response

## Internal Architecture

### Execution Driver Internals

`AbstractExecutionDriver<T>` is the base class for both `OrchestratedDriver` and `ChoreographedDriver`. It implements a five-phase iteration loop:

1. **Route** -- invoke `RoutingStrategy.route()` with current candidates and state
2. **Activate** -- evaluate `ActivationRule.shouldActivate()` for each selected agent
3. **Dispatch** -- invoke agents via `AgentInvoker` (handles ExternalAgent, ComposedAgent natively; ChannelAgent/WorkerAgent/HumanAgent require custom invokers)
4. **Aggregate** -- run `AggregationStrategy.aggregate()` on collected results
5. **Terminate** -- evaluate `TerminationCondition.evaluate()` to decide Continue/Complete/Failed/Escalate

Key internal state:
- `AtomicReference<ExecutionState>` -- sealed 7-state machine (Idle -> Running -> WaitingForAgent -> ... -> Complete/Faulted/Cancelled)
- `Map<AgentRef, Integer> activationCounts` -- per-agent activation tracking
- `Map<AgentRef, Integer> consecutiveIdleCounts` -- idle detection for activation context
- `volatile boolean cancelled` -- cooperative cancellation

`OrchestratedDriver` runs the loop imperatively (while loop, transitions to Running each iteration). `ChoreographedDriver` runs it reactively (transitions to WaitingForEvent between iterations).

### Pattern Builder Architecture

All 8 pattern builders extend `AbstractPatternBuilder<T, B>` (CRTP pattern). The base class provides:
- Fluent setters for all five SPIs plus failure policy, listeners, task description, backend
- `build()` -- assembles an `ExecutionModel<T>` with the configured SPIs
- `execute(T)` -- builds and runs via the configured `ExecutionBackend` (defaults to `OrchestratedDriver`)
- `agents(AgentRef...)` / `agents(RoutingCandidate...)` -- sets the candidate supplier

Each builder overrides defaults for its pattern topology:
| Builder | Default Routing | Default Aggregation | Default Termination |
|---------|----------------|--------------------|--------------------|
| `SupervisorBuilder` | FirstMatch or LlmSelected | PassThrough | MaxIterations(10) |
| `SequenceBuilder` | Sequential | PassThrough | After N iterations |
| `LoopBuilder` | RoundRobin | PassThrough | MaxIterations(default 10) |
| `ParallelBuilder` | Select all | CollectAll | After 1 iteration |
| `VotingBuilder` | Select all | MajorityVote | After 1 iteration |
| `DebateBuilder` | RoundRobin | CollectAll | MaxIterations or JudgeConvergence |
| `ConditionalBuilder` | FirstMatch | PassThrough | After 1 iteration |
| `HtnBuilder` | Sequential | CollectAll | After 1 iteration (dynamically adjusted) |

`HtnBuilder` is special: its `execute(T)` override flattens the HTN tree via decomposition, builds a dynamic `ExecutionModel` with agent count derived from the plan, then runs via `OrchestratedDriver`.

### Agentic Decomposition Strategies

Nine decomposition strategies with increasing sophistication:

| Strategy | Approach | Key Internal Detail |
|----------|----------|---------------------|
| `IdentityDecomposition` | Pass-through -- wraps single task as-is | Throws on CompoundTask input |
| `StaticDecomposition` | Pre-defined task breakdown via `DecompositionMethod` | First-match guard evaluation; pre-filters methods by `estimatedCost`/`estimatedDuration` against `context.constraints()` |
| `SequenceStrategy` | Package-private: resolves children sequentially using composed decomposer | Used internally by `Tasks.compound()` and `Tasks.decompose()` |
| `ForwardReasoningDecomposition` | SHOP-style forward reasoning -- applies `PrimitiveTask.effect()` to projected state | Uses `UnaryOperator<T> stateCopier` to clone state before effect application |
| `LlmDecomposition` | Recursive multi-level LLM planning via `maxDepth` | Parses JSON: `[{"agent":..., "task":..., "rationale":...}]` for leaves, `{"subtask":..., "description":...}` for compounds |
| `HybridDecomposition` | Static-first with LLM fallback on `NoMethodMatchedException` | Passes `staticFailureHint` (failed method's task name + method count) to LLM context |
| `CapabilityDependencyDecomposition` | GOAP backward-chaining from goal state | Matches goal types to agent capabilities via preconditions/effects, builds dependency DAG via `CapabilityDependencyContext` |
| `HeuristicDecomposition` | Ranked method selection via `DecompositionHeuristic<T>` | Tries methods in score order, backtracks on `NoMethodMatchedException`. Uses `ScoredMethod<T>` |
| `CompositeHeuristic` | Weighted combination of multiple `DecompositionHeuristic<T>` | Min-max normalisation across delegates. Inner: `WeightedHeuristic<T>(heuristic, weight)` |

**Heuristic implementations:**
- `StructuralCostHeuristic<T>` -- tree cost estimation. Leaf = 1.0, compound = min of methods, opaque = configurable. Scores by negative cost (lower cost = better).
- `LlmDecompositionHeuristic<T>` -- asks LLM to score methods 0.0-1.0 via JSON response. Falls back to equal scores on failure.

**Task types:**
- `PrimitiveTask<T>` -- leaf with precondition, effect, and output contract. Effect: `Consumer<T>` applied to state during forward reasoning.
- `PlannedTask<T>` -- leaf with rationale. Created by LLM decomposition. No precondition/effect.
- Both implement `TaskNode.LeafTask<T>` (from engine-api) and carry an `AgentRef`.

**DSL factories:**
- `Decomposition` -- strategy-level factories: `none()`, `staticTree()`, `goap()`, `hybrid()`, `heuristic()`. Also `method()`, `sequence()`, `primitive()`, `compound()`, `planned()`.
- `Tasks` -- tree-construction-level factories: `primitive()` (4 overloads with effect/description), `planned()` (2 overloads), `compound()` (2 overloads: with methods or with children), `decompose()` (2 overloads: with/without guard predicate).

### Conversation Projection and Fold Architecture

`ConversationProjection` is the abstract base for channel projections. It implements `ChannelProjection<ConversationState>` and delegates to `ConversationFold` for state transitions. The dispatch logic:

1. Parse metadata from message body via `ChannelMessageMeta.parseMeta(sentinel(), content)`
2. Extract entry type from metadata (`ConversationProtocol.ENTRY_TYPE`)
3. If entry type is in `skipEntryTypes()` set, skip the message
4. Dispatch infrastructure types directly: MEMO -> `ConversationFold.addMemo()`, SUB_TASK_REQUEST -> `requestSubTask()`, SUB_TASK_FINDING -> `completeSubTask()`, SUB_TASK_ERROR -> `errorSubTask()`, FLAG_HUMAN -> `flagHuman()`
5. For domain types: if `isPointInitiator(entryType)` -> `createPoint()`, otherwise -> `respondToPoint()` with `statusAfter(entryType)`

`ConversationFold` is purely functional -- every method takes a `ConversationState` and returns a new `ConversationState`. No I/O, no parsing. Eight operations: createPoint, respondToPoint, reprioritisePoint, flagHuman, addMemo, requestSubTask, completeSubTask, errorSubTask.

### Epistemic Common Ground Pipeline

Three-stage analysis pipeline:

1. **CommonGroundAnalyser.analyse()** -- iterates all conversation points, builds `ParticipantContext` for each (who participated, responded, acknowledged, completed, disputed, failed), applies the `EpistemicRule` to classify each point as ESTABLISHED/PENDING/DISPUTED, produces `CommonGroundState`.

2. **ConvergenceAnalyser.analyse()** -- builds `ConvergenceContext` (similarity metrics, staleness measures, message-type distribution), delegates to `ConvergencePolicy.evaluate()` for final signal.

3. **ConvergenceTermination** -- bridges into the agentic termination SPI. Extracts `ConversationState` from execution state via `stateExtractor`, runs both analysers, maps CONSENSUS/DIMINISHING_RETURNS to `Complete`, DEADLOCK to `Escalate`, others to `Continue`.

**Built-in EpistemicRules:** `explicitAcknowledgement(minParticipants)` -- N distinct responders needed. `tacitAcceptance(windowRounds)` -- silence after N rounds = established. `commitmentResolution()` -- DONE message = established.

**Built-in ConvergencePolicies:** `structural(threshold, staleRounds)` -- Jaccard similarity + staleness. `commonGroundRatio(consensusThreshold, deadlockRatio)` -- established/total ratio. `composite(policies...)` -- picks highest-severity signal.

### Agentic Accountability Listeners

Three listeners implementing `ExecutionEventListener`:

| Listener | Sink Type | Purpose |
|----------|-----------|---------|
| `EventLogListener` | `EventSink` (@FunctionalInterface: `record(OrchestrationEventType, Map<String, Object>)`) | Operational audit |
| `LedgerExecutionListener` | `LedgerSink` (@FunctionalInterface: `record(OrchestrationEventType, actorId, actorRole, Map<String, Object>)`) | EU AI Act Art.12 compliance audit |
| `MetricsListener` | OTel `Meter` | Telemetry: agent duration histogram, routing/activation counters, failure counter, execution duration/iterations histograms |

`OrchestrationEventType` enum: `EXECUTION_STARTED`, `ROUTING_DECISION`, `ACTIVATION_EVALUATED`, `AGENT_DISPATCHED`, `AGENT_RESULT`, `AGENT_FAILED`, `AGGREGATION_COMPLETED`, `TERMINATION_EVALUATED`, `EXECUTION_COMPLETED`.

### Routing Agent Internals

`RoutingSupport` is the package-private utility shared by both `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy`. Private constructor. Key internal operations:

- **Trust filtering** via `applyTrustFilter()` -- four-phase trust maturity model using optional `TrustCandidateClassifier`, `TrustScoreSource`, and `TrustRoutingPolicyProvider`. Returns sealed `TrustFilterOutcome`:
  - `Proceed(eligible, classified)` -- trust check passed, continue with filtered candidates
  - `Decided(RoutingResult)` -- terminal decision (escalation or classifier fallback)
- **Prompt construction** via `buildUserPrompt()` and `buildCandidateCard()` -- formats capability + context + candidate cards
- **LLM invocation** via `invokeAndCollect()` -- invokes `AgentProvider`, collects `TextDelta` events
- **Response parsing** via `parseSelection()` and `extractAgentName()` -- handles JSON with markdown fences

**Five `RoutingSignalProvider` implementations:**
- `PlanCompositionAnalyser` (id: `"plan-composition"`) -- case-level outcomes in multi-step plans. Requires planTrace.size() >= 2. Weighted by `CbrCaseOutcomeWeights`.
- `PredecessorAnalyser` (id: `"predecessor"`) -- immediate predecessor context in historical plan traces. Records predecessor (capability:worker) pair in signal reason.
- `CoordinationSignalProvider` (id: `"coordination"`) -- team composition outcomes with adaptation-guided retrieval. Calculates team overlap with eligible pool. Weighted by `CoordinationOutcomeWeights`.
- `DispositionAwareRouting` (id: `"disposition"`) -- personality/disposition match scoring. Extracts `DispositionProfile` from case context JSON at `_routing.disposition.<capabilityName>` or `_routing.disposition.default`. Each `DispositionAxis` is weighted; exact match = 1.0, missing = 0.5, mismatch = 0.0.
- `CbrRoutingPromptSection` -- not a signal provider, but a `RoutingPromptSection` that renders per-agent CBR statistics for LLM routing prompts.

**Outcome weight SPIs (all have `@DefaultBean @ApplicationScoped` defaults):**
- `CbrOutcomeWeights` -- step-level `Map<RoutingOutcome, Double>`. Default: SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, FAILURE=0.0.
- `CbrCaseOutcomeWeights` -- case-level `Map<String, Double>`. Default: COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0.
- `CoordinationOutcomeWeights` -- case-level `Map<String, Double>`. Default: COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0.

Domain repos override these by providing `@ApplicationScoped` beans without `@DefaultBean`.

### Summarisation Pipeline Internals

**Core pipeline:** `EventAccumulator` (synchronized buffer) -> optional `Compactor<E>` (pre-processing) -> `Summariser<IN, OUT>` (async transformation) -> `EventStreamBus` (pub/sub distribution).

`SummarisationRunner` orchestrates this for flat event streams. `KeyedSummarisationRunner` adds grouping via `KeyedAccumulator` (groups by key, drains completed/stale groups independently).

**Tick semantics:** `tick(now)` checks if the `WindowPolicy` is satisfied (by age or count), drains if ready, runs compactor + summariser, publishes to output bus. `flush()` does an unconditional drain regardless of policy -- use at shutdown. Both return `CompletionStage<Void>` for async summarisers.

**Content summarisation layer:** `ContentSummariser<T>` is a higher-level contract decoupled from `LevelEvent` wrappers. `ContentSummariserToSummariser<T>` adapts it to `Summariser<T, String>`. `TieredContentSummariser<T>` routes to different strategies based on batch size. `VerbatimContentSummariser<T>` renders each item as a bullet point (no LLM). `LlmContentSummariser<T>` uses `AgentProvider` with configurable EDIT/APPEND mode.

### Summarisation Observation Pipeline

Terminal consumer of the summarisation pipeline -- tiered, demand-driven rendering for LLM agent prompts.

- `ObservationAccumulator<E>` -- thread-safe buffer with demand-driven drain via `ObservationRenderer`. Tracks `lastDrainTimestamp` for inter-observation timing.
- `TieredObservationRenderer<E>` -- three-tier rendering:
  - Below `verbatimThreshold`: individual events with timestamps
  - Between thresholds: grouped by key with group summaries
  - Above `groupedThreshold`: delegated to `Summariser<E, String>` for LLM/algorithmic summarisation
  - `withHeaderFormatter()` for custom header formatting
- `PartitionedObservationService<E, K>` -- multi-observer context management. Events routed to observers and partitions via `VisibilityPolicy`. On drain, returns current partition's result plus cached "remembered" results from other visible partitions. Uses `ConcurrentHashMap` for observer state.

### Affordance Rendering

`AffordanceRenderer` renders entity-grounded observations for LLM agents:
- `renderEntities(List<ObservableEntity>)` -- identity + affordance bracket notation (e.g., `Entity: name [action1, action2]`)
- `renderObservation(List<ObservationSection>)` -- assembles sections (EntityGroup / TextBlock / ItemList) into plain text
- `renderActionVocabulary(header, List<ActionDescriptor>)` -- formats available action types with descriptions and parameter formats
- `withHeaderFormatter(Function<String, String>)` -- returns a new renderer with custom header formatting

`ObservationSection` is a sealed interface with three variants: `EntityGroup`, `TextBlock`, `ItemList`. Static factories: `entities()`, `text()`, `items()`.

### Drive Architecture (Compositor Pattern)

The drive sub-package (`agentic.social.drive`) follows a *compositor pattern* that deliberately breaks the universal `record()` + `tick()` contract used by all six social cognition orchestrators. Drives consume derived state from other orchestrators rather than accumulating raw signals, so `record()` has nothing to accumulate.

**Lifecycle:** `DriveOrchestrator.tick(agentId, tenantId, AgentDescriptor)` evaluates four `DriveSource` implementations, delegates modulation and composition to `DriveComposer`, and caches the resulting `DriveProfile`. `currentDrives(agentId, tenantId)` returns the cached state. Per-agent `ReentrantLock` guards `tick()` -- same concurrency model as other orchestrators.

**DriveSource SPI:** `@FunctionalInterface` with `DriveIntensity evaluate(agentId, tenantId)`. Each implementation takes its source orchestrator as a constructor dependency and reads its public accessors. Each concrete implementation caches the intermediate data read during `evaluate()` and exposes it via a typed accessor (`lastGaps()`, `lastTrend()`, `lastProfiles()`, `lastSnapshots()`). Goal mappers consume this cached data instead of re-querying the source orchestrators. `DriveOrchestrator` exposes concrete sources via `curiosityDrive()` etc. (null for lambda fallbacks). Analogous to `TraitPressureSource<E>` in personality evolution, but pull-based (reads at tick-time) rather than push-based (translates events at record-time).

**Modulation flow:** Raw intensities from four sources → mood modulation (PAD axes from `MoodOrchestrator.currentMood()`) → personality modulation (`AgentDescriptor.disposition()` weights per `DispositionAxis`) → intensity clamping → weighted composition → `DriveProfile`.

**CDI management:** `DriveOrchestrator` and `DriveComposer` are `@ApplicationScoped`. DriveOrchestrator's `@Inject` constructor takes `Instance<MemoryHygieneOrchestrator>` (optional -- curiosity drive returns zero when hygiene is not CDI-managed), `StrategyLearningOrchestrator`, `UserModelOrchestrator`, `MentalModelOrchestrator`, `MoodOrchestrator`, `DriveComposer`, and `DriveConfig`. Drive sources are constructed internally -- they are not CDI beans. Per-drive config params (affiliation decay threshold, stale duration, autonomy confidence floor) are in `DriveConfig`.

**Lifecycle wiring:** `InnerLifeOrchestrator` injects `DriveOrchestrator` and calls `tick()` at the start of `doTick()`. This ensures drives are computed before inner life logic runs. The scheduler owns the full tick ordering: source orchestrators → `DriveOrchestrator` → `GoalProposalOrchestrator`.

**Observation rendering:** `CognitiveObservationSections.motivationalStateSection(DriveProfile)` renders the drive profile as an `ObservationSection.ItemList`. Per-axis items show intensity (1dp) and trigger provenance. Axes below 0.05 intensity are filtered. Downstream apps include this section in their `WorldObservationProvider` implementation.

**Upstream API additions for drive sources:**
- `MemoryHygieneOrchestrator.knowledgeGaps()` → `KnowledgeGapSummary` (in `blocks.memory`)
- `StrategyLearningOrchestrator.engagementTrend()` → `EngagementTrend` (in `blocks.agentic.social`)
- `UserModelOrchestrator.activeProfiles()` → cross-subject profile aggregation
- `MentalModelOrchestrator.activeSnapshots()` → cross-subject mental model aggregation

### Goal Proposal (Compositor Pattern)

The goal sub-package (`agentic.social.goal`) is Layer 2 of the autonomous intelligence stack. It translates drive signals into concrete `AgentGoal` proposals that the engine's existing goal lifecycle can decompose and execute.

**Compositor guarantee:** `GoalProposalOrchestrator.tick()` evaluates mappers, caches proposals, and returns the result -- no side effects. The scheduler reads `currentProposals()` (or the tick result) and calls `GoalFormationService.propose()` (engine-api) to register goals. This enables inspection of proposals before registration and aligns with the engine's `autoApprove` governance.

**DriveGoalMapper SPI:** `@FunctionalInterface` with `@Nullable DriveGoalProposal evaluate(agentId, tenantId, DriveIntensity)`. Each implementation injects the corresponding concrete `DriveSource` (e.g., `CuriosityGoalMapper` takes `CuriosityDrive`) and reads cached intermediate data via the `last*()` accessor. Returns null when no goal is warranted or cached data is null. Four implementations: `CuriosityGoalMapper`, `CompetenceGoalMapper`, `AffiliationGoalMapper`, `AutonomyGoalMapper`.

**Capacity and lifecycle:** Drive-sourced goals occupy a separate budget (`maxDriveGoals=3` of `MAX_GOALS=10`). Proposals ranked by drive intensity when exceeding capacity. Relevance re-evaluation abandons goals when the originating drive drops below `relevanceThreshold` for longer than `staleAfter`. Failure-abandoned goals (via `GoalSignalStore`) are suppressed from re-proposal.

**Engine integration (Batch 2):** `GoalFormationService` SPI in engine-api extracts case-independent goal registration (validation, deduplication, capacity, audit) from `GoalFormationEvaluator`. `GoalRemovalService` SPI provides reusable goal removal with audit. `ProposedGoal` gains `@Nullable Map<String, String> attributes` for goal provenance. Drive-sourced goals store `{"source": "drive", "driveAxis": "CURIOSITY"}`.

**Consumer integration:** The scheduler (quarkmind, claudony, etc.) wires the full tick loop -- source orchestrators → DriveOrchestrator → GoalProposalOrchestrator → GoalFormationService. See spec §Consumer Integration Example for the complete flow.

### Narrative Identity (Compositor + Effectful Split)

Layer 3a of the autonomous intelligence stack. Agents construct a first-person autobiography from accumulated reflections. The architecture splits into effectful synthesis and side-effect-free orchestration:

- **NarrativeSynthesiser** (`@ApplicationScoped`, effectful) -- composite gate evaluation (count + novelty + quiet period), LLM synthesis via `AgentProvider`, writes `NarrativeState` to `NarrativeStore`. Called by the consumer's scheduler before the compositor ticks.
- **NarrativeOrchestrator** (`@ApplicationScoped`, compositor) -- reads `NarrativeStore`, caches `NarrativeState` per agent, returns `NarrativeTick` (Updated/NoChange). No LLM, no side effects.
- **GroupNarrativeOrchestrator** (compositor, not CDI-managed) -- GROUP-scoped mirror of NarrativeOrchestrator. Consumer constructs with `Set<String> memberIds`. `tick(groupId, tenantId)` detects new `GroupEpisode`s and themes.

**Persistence:** `NarrativeStore` SPI with `CbrNarrativeStore` `@DefaultBean`. `NarrativeStateSchema` handles polymorphic `NarrativeFragment` serialization (type discriminators for `IndividualEpisode`/`GroupEpisode`/`DerivedTheme`). `ReflectionQueryStore` SPI provides read access to stored reflections for synthesis.

**Drive modulation:** `NarrativeModulation.compute(NarrativeState)` converts themes to per-axis drive coefficients. `DriveOrchestrator` reads from `NarrativeOrchestrator` via `Instance<>` (optional) and passes modulation to `DriveComposer`. Works identically for individual and group narratives.

**Tick ordering:** Source orchestrators → NarrativeSynthesiser → NarrativeOrchestrator → DriveOrchestrator → GoalProposalOrchestrator.

### Social Emergence

Layer 3b -- collective behaviors emerging from multi-agent interaction.

- **SocialNormDetector** (`@ApplicationScoped`, compositor) -- reads `NormObservation` cases from CBR, groups by `behavioralPattern`, computes adherence rates, classifies strength (EMERGING/ESTABLISHED/DECLINING). Per-tenant caching with prior-state tracking for decline detection. `NormObservationSchema` for CBR serialization.
- **CollectiveGoalFormation** (compositor, not CDI-managed) -- reads N agents' `DriveProfile`s, computes pairwise alignment (`1 - |diff|` per axis), forms groups via connected components, proposes `CollectiveGoalProposal`s when composite alignment exceeds threshold. Per-group cooldown. `toJointIntention()` bridges proposals to `JointIntention.form()`.

**Consumer construction:** `CollectiveGoalFormation` takes `DriveOrchestrator`, `List<String> agentIds`, and `CollectiveGoalConfig` at construction. The agent set is deployment-time configuration -- consumers produce the bean via `@Produces` or construct directly.

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
| #23 | Oversight gate lifecycle + risk classification | Done | engine-api / openclaw |
| #24 | Universal pluggable routing strategy | Moved -> engine#634 | engine |
| #30 | AI routing strategy impls (trust, LLM, CBR) | Done | blocks |
| #25 | Worker data coordination (DataExchange/DataChannel) | Moved -> engine#633 | engine |
| #27 | Layered event summarisation | Done | blocks |

## Depended On By

| Repo | What it uses |
|------|-------------|
| casehub-drafthouse | Channel + conversation blocks -- DebateProtocol delegates to `ConversationProtocol`, DebateChannelProjection extends `ConversationProjection`, `ChannelAgentDispatcher`, `BoundedProjectionDecorator`, `ContextTracker` |
| casehub-engine | `ExecutionModel`, `ExecutionDriver`, `ExecutionResult`, `AgentRef`, `RoutingCandidate`, agentic SPIs |
| casehub-quarkmind | Summarisation: `SummarisationRunner`, `EventStreamBus`, `EventAccumulator`, `WindowPolicy`, `Summariser`, `EventLevel`, `LevelEvent` |

## Design Documents

- [Agentic orchestration research](https://raw.githubusercontent.com/casehubio/blocks/main/docs/agentic-orchestration-research.md)
- [Execution backend architecture](https://raw.githubusercontent.com/casehubio/blocks/main/docs/execution-backend-architecture.md)
- [Unified execution model](https://raw.githubusercontent.com/casehubio/blocks/main/docs/unified-execution-model.md)
- [Conversation projections vs chat logs](https://raw.githubusercontent.com/casehubio/blocks/main/docs/conversation-projections-vs-chat-logs.md)
