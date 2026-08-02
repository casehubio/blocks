# casehub-blocks -- Consumer Guide

> Reusable building blocks for CaseHub applications -- LLM integration, agentic orchestration, structured conversations, trust routing, and temporal summarisation.

**GitHub:** [casehubio/blocks](https://github.com/casehubio/blocks)
**Tier:** Foundation-adjacent (between foundation and application)

---

## What This Module Does

Packages recurring cross-application patterns that require LLM integration, classical AI, or foundational API composition. Single module, single artifact: `casehub-blocks`.

Includes a full agentic orchestration framework with DAG-based execution plans, hybrid (static + LLM) task decomposition, and composable routing/aggregation/termination strategies.

## Scope Criteria

A pattern belongs in blocks if it meets at least one of these criteria:

1. **Needs an LLM in the loop** -- the pattern involves LLM invocation, prompt construction, or LLM-driven decision-making
2. **Uses classical AI** -- classical planning, Bayesian reasoning, CEP (complex event processing), or similar
3. **Requires integration across foundational platform parts that the consuming module does not already depend on** -- the pattern composes across qhorus, engine, work, or eidos APIs in combinations that would otherwise force every consumer to take on new cross-module dependencies. If all dependencies are already available in the consuming module's API tier, the type belongs in that API module, not blocks.

**The test:** if removing the LLM/AI/integration aspect leaves a generic utility, it belongs in platform. If removing the domain-specific aspect leaves a reusable AI-integration pattern, it belongs in blocks.

## Packages

### `io.casehub.blocks.channel`

Channel utility blocks -- message metadata encoding, context tracking, bounded projection, and agent dispatch coordination.

| Class | What it does |
|-------|-------------|
| `ChannelMessageMeta` | Sentinel-prefixed key=value metadata headers in message bodies. Apps choose their own sentinel. Methods: `parseMeta()`, `bodyContent()`, `encode()`, `parseInt()` |
| `ContextTracker` | Incremental LLM context window usage tracking via atomic counters. Thread-safe. |
| `ContextSnapshot` | Immutable record of context state: contribution chars, window size, effective %, threshold exceeded |
| `BoundedProjectionDecorator<S>` | Generic decorator wrapping any qhorus `ChannelProjection<S>` -- skips messages past a configurable bound. Consumer supplies the value extraction function. |
| `ChannelAgentHandler` | SPI interface for sub-task handlers: `handles()`, `prepareTask()`, `buildResponse()`. First-match routing. |
| `ChannelAgentDispatcher` | First-match handler routing + agent invocation. Takes `Function<AgentTask, String>` (agent provider) and `Consumer<MessageDispatch>` (message sink). Subclass to override `onError()`. |
| `ChannelAgentRequest` | Record: channelId, correlationId, message (the sub-task trigger) |
| `AgentTask` | Record: systemPrompt, assembledInput (what to send to the LLM) |
| `ChannelEventAdapter<E>` | Bridge: implements `MessageObserver`, extracts domain events via extractor function, publishes `LevelEvent<E>` to an `EventStreamBus`. |
| `ChannelEventPublisher<E>` | Reverse bridge: subscribes to `EventStreamBus<E>`, dispatches events back to qhorus channels via `MessageDispatcher`. |

### `io.casehub.blocks.conversation`

Structured conversation protocol -- reusable infrastructure for multi-agent deliberation channels. Extracted from drafthouse.

| Class | What it does |
|-------|-------------|
| `ConversationProtocol` | Sentinel-based metadata encoding/decoding for structured conversation messages. Defines entry types, round markers, status transitions. |
| `ConversationProjection` | Abstract base class for conversation-style channel projections. Folds channel messages into `ConversationState` by dispatching on metadata entry types. Configurable vocabulary per consumer via hook methods: `sentinel()`, `isPointInitiator(entryType)`, `statusAfter(entryType)`. |
| `ConversationFold` | Fold operations for typed-message projections -- accumulates conversation state from a message stream. |
| `ConversationState` | Immutable snapshot of conversation state: points by thread, round boundaries, flags, sub-task status. |
| `ConversationPoint` | Individual point in a conversation thread -- classification, priority, content, agent attribution. |
| `ConversationRenderer` | Pluggable markdown rendering of conversation state -- round-by-round or thread-by-thread views. |
| `PointClassification` | Open type system for classifying conversation points (replaces closed enum patterns). |
| `CommonGroundAnalyser` | Stateless utility: `analyse(ConversationState, EpistemicRule) -> CommonGroundState`. Partitions points into established, pending, and disputed. |
| `ConvergenceAnalyser` | Stateless utility: `analyse(ConversationState, CommonGroundState, ConvergencePolicy, recentWindow) -> ConvergenceSignal`. |

### `io.casehub.blocks.agentic`

Compositional agentic orchestration framework -- nine sub-packages implementing five SPIs for routing, decomposition, activation, aggregation, and termination, plus execution drivers and pre-composed pattern builders.

| Sub-package | What it provides |
|-------------|-----------------|
| `agentic` | Foundation types: `AgentRef` (sealed: WorkerAgent, ChannelAgent, HumanAgent, ExternalAgent, ComposedAgent), `AgentResult`, `RoutingCandidate`, `FailurePolicy`, `AgentCardSupport` |
| `agentic.routing` | Routing SPI: `RoutingStrategy<T>`, `RoutingDecision` (sealed: Selected, Unresolvable, Escalate), `FirstMatchRouting`, `RoundRobinRouting`, `SequentialRouting`, `LlmSelectedRouting` |
| `agentic.decomposition` | Decomposition SPI: `DecompositionStrategy<T>`, `TaskNode` (sealed: LeafTask / CompoundTask), `IdentityDecomposition`, `StaticDecomposition`, `LlmDecomposition`, `HybridDecomposition`, `ForwardReasoningDecomposition`, `GoalOrientedDecomposition`, `HeuristicDecomposition` |
| `agentic.plan` | `ExecutionPlan<T>` -- DAG record expressing task dependencies. Validated: no cycles, all references exist. Factory methods: `singleton()`, `sequence()`, `parallel()`, `fromList()` |
| `agentic.activation` | Activation SPI: `ActivationRule<T>`, `OnExplicitDispatch`, `MaxIterationsGuard` |
| `agentic.aggregation` | Aggregation SPI: `AggregationStrategy<T>`, `AggregationResult` (sealed: Resolved, Partial, Deadlocked), `PassThrough`, `CollectAll`, `MajorityVote` |
| `agentic.termination` | Termination SPI: `TerminationCondition<T>`, `TerminationDecision` (sealed: Continue, Complete, Failed, Escalate), `GoalReached`, `MaxIterationsTermination`, `JudgeConvergence` |
| `agentic.model` | Execution model: `ExecutionDriver<T>`, `OrchestratedDriver`, `ChoreographedDriver`, `AgentInvoker<T>`, `ExecutionBackend<T>`, `ExecutionResult` (sealed: Completed, Failed, Escalated, Cancelled) |
| `agentic.pattern` | Pattern DSL: `Patterns` entry point, 8 builders (Supervisor, Sequence, Loop, Parallel, Voting, Debate, Conditional, HTN) |

### `io.casehub.blocks.routing`

Trust routing audit types -- compliance records for trust-weighted routing decisions.

| Class | What it does |
|-------|-------------|
| `RoutingDecisionRecord` | Compliance audit record: capabilityTag, workerId, trustScoreAtRouting, thresholdApplied, evidenceEntryId. |
| `TrustRoutingRequirement` | Compliance evidence wrapper: requirementId, citation, mechanism, status, decisions. |
| `RequirementStatus` | Enum: CLOSED, PARTIAL, BREACHED, GAP. |

### `io.casehub.blocks.routing.agent`

AI-powered `AgentRoutingStrategy` implementations for the engine's routing pipeline. Strategies are selected by name via `StrategyResolver` (engine#634). Optional trust classification via `Instance<T>` -- activates when engine-ledger is on the consumer's classpath.

| Class | What it does |
|-------|-------------|
| `LlmAgentRoutingStrategy` | Strategy id: `"llm"`. LLM reasoning about which candidate best fits the task. Composable prompt enrichment via `RoutingPromptAssembler`. |
| `CbrAgentRoutingStrategy` | Strategy id: `"cbr"`. Case-based evidence with similarity-weighted scoring and `RoutingSignalAssembler` integration. Falls back to `AgentGraphQuery.topAgentsByOutcome()`. |
| `PlanCompositionAnalyser` | `RoutingSignalProvider` (id: `"plan-composition"`). Scores candidates based on case-level outcomes in multi-step plans. |
| `CbrRoutingPromptSection` | `RoutingPromptSection` -- renders historical CBR outcomes per eligible agent for LLM routing prompts. |
| `CoordinationSignalProvider` | `RoutingSignalProvider` (id: `"coordination"`). Scores candidates by historical team composition outcomes. |

### `io.casehub.blocks.summarisation`

Layered event summarisation framework -- temporal event accumulation with configurable window policies and pluggable summarisation strategies. Pure Java, zero CDI/Quarkus dependencies.

| Class | What it does |
|-------|-------------|
| `EventLevel` | Record: named level in the temporal hierarchy |
| `LevelEvent<E>` | Record: typed event at a specific level with timestamp |
| `WindowPolicy` | Configurable window boundaries (time-based, count-based, or both) |
| `EventAccumulator<E>` | Thread-safe event buffer with atomic check+drain |
| `EventStreamBus<E>` | Predicate-based pub/sub for streaming events through the pipeline |
| `Summariser<IN, OUT>` | `@FunctionalInterface` SPI for pluggable summarisation strategies |
| `SummarisationRunner<IN, OUT>` | Wires accumulator -> summariser -> output bus. Tick-driven. |
| `KeyedAccumulator<K, E>` | Groups events by key, emits each group on completion or stale timeout |
| `KeyedSummarisationRunner<K, IN, OUT>` | Grouped variant -- per-key summarisation with independent failure recovery |
| `ContentSummariser<T>` | `@FunctionalInterface` SPI: batch summarisation decoupled from pipeline event model |
| `TieredContentSummariser<T>` | Dispatches to delegates based on batch size thresholds |

## Key Integration Patterns

**Summarisation Pattern A** (SummarisationRunner pipeline): sync heuristics, microsecond latency. Wire accumulator -> optional compactor -> summariser -> output bus.

**Summarisation Pattern B** (direct EventAccumulator): async LLM dispatch, caller manages the accumulator lifecycle.

**Channel bridges**: `ChannelEventAdapter` (channel -> event bus) and `ChannelEventPublisher` (event bus -> channel) provide bidirectional integration between qhorus channels and the summarisation pipeline.

## Configuration

No runtime configuration -- blocks is a pure library, not a Quarkus extension. All configuration happens via code (SPI implementations, CDI beans).

**CDI indexing:** blocks does not include a Jandex index. Consumers that need blocks' CDI beans (routing strategies, channel summarisers) must opt in:
```properties
quarkus.index-dependency.casehub-blocks.group-id=io.casehub
quarkus.index-dependency.casehub-blocks.artifact-id=casehub-blocks
```
Consumers that only use blocks' pure types (records, sealed interfaces, plain classes) need no configuration.

## Boundary Rules

- Does NOT provide generic utilities (backoff, rate limiters) -- those belong in platform
- Does NOT do pure SPI unifications -- those stay in the API module that owns the lifecycle
- Does NOT contain domain-specific logic that happens to be duplicated but lacks AI or foundational integration
- Does NOT own trust score computation -- that is ledger
- Does NOT own classical routing strategy execution -- that is engine
- Does NOT own policy configuration SPIs -- `TrustRoutingPolicyProvider` is in engine-api

## Dependencies

**Compile:** `casehub-qhorus-api`, `casehub-work-api`, `casehub-engine-api`, `casehub-eidos-api`, `casehub-worker-api`, `org.jspecify:jspecify`

**Provided:** `io.smallrye.reactive:mutiny`, `casehub-platform-agent-api`, `casehub-platform-api`, `casehub-engine-ledger`, `casehub-ledger-api`, `casehub-neocortex-memory-api`, `io.opentelemetry:opentelemetry-api`

**Test:** `casehub-qhorus`, `casehub-qhorus-testing`, `casehub-engine`, `casehub-engine-testing`, `assertj`, `mockito`, `awaitility`, `io.opentelemetry:opentelemetry-sdk-testing`
