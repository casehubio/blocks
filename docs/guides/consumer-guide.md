# casehub-blocks -- Consumer Guide

> Reusable building blocks for CaseHub applications -- LLM integration, agentic orchestration, structured conversations, trust routing, and temporal summarisation.

**GitHub:** [casehubio/blocks](https://github.com/casehubio/blocks)
**Tier:** Foundation-adjacent (between foundation and application)

---

## What This Module Does

Packages recurring cross-application patterns that require LLM integration, classical AI, or foundational API composition. Single module, single artifact: `casehub-blocks`.

Includes a full agentic orchestration framework with DAG-based execution plans, hybrid (static + LLM) task decomposition, composable routing/aggregation/termination strategies, and a pattern DSL for common multi-agent topologies.

## Scope Criteria

A pattern belongs in blocks if it meets at least one of these criteria:

1. **Needs an LLM in the loop** -- the pattern involves LLM invocation, prompt construction, or LLM-driven decision-making
2. **Uses classical AI** -- classical planning, Bayesian reasoning, CEP (complex event processing), or similar
3. **Requires integration across foundational platform parts that the consuming module does not already depend on** -- the pattern composes across qhorus, engine, work, or eidos APIs in combinations that would otherwise force every consumer to take on new cross-module dependencies. If all dependencies are already available in the consuming module's API tier, the type belongs in that API module, not blocks.

**The test:** if removing the LLM/AI/integration aspect leaves a generic utility, it belongs in platform. If removing the domain-specific aspect leaves a reusable AI-integration pattern, it belongs in blocks.

## Packages

### `io.casehub.blocks.channel`

Channel utility blocks -- message metadata encoding, context tracking, bounded projection, and agent dispatch coordination.

| Class | Type | What it does |
|-------|------|-------------|
| `ChannelMessageMeta` | final class | Sentinel-prefixed key=value metadata headers in message bodies. Apps choose their own sentinel. Static methods: `parseMeta()`, `bodyContent()`, `encode()`, `parseInt()` |
| `ContextTracker` | class | Incremental LLM context window usage tracking via atomic counters. Thread-safe. Methods: `addContribution()`, `addInitialContribution()`, `reportAgentUsage()`, `snapshot()` |
| `ContextSnapshot` | record | Immutable snapshot of context state: `serverContributionChars`, `windowSizeChars`, `agentReportedPercent`, `messageCount`, `effectivePercent`, `thresholdExceeded` |
| `BoundedProjectionDecorator<S>` | class | Decorator wrapping any qhorus `ChannelProjection<S>` -- skips messages past a configurable bound. Consumer supplies a `ToIntFunction<MessageView>` value extraction function. |
| `ChannelAgentHandler` | interface (SPI) | Sub-task handler contract: `handles(request)`, `prepareTask(request)`, `buildResponse(channelId, senderId, llmOutput, trigger)`. First-match routing. |
| `ChannelAgentDispatcher` | class | First-match handler routing + agent invocation. Constructor takes `Function<AgentTask, String>` (agent provider), `Consumer<MessageDispatch>` (message sink), `Iterable<ChannelAgentHandler>` (handlers), and `String senderId`. Override `onError()` for app-specific error dispatch. |
| `ChannelAgentRequest` | record | Sub-task request: `channelId` (UUID), `correlationId` (String), `message` (OutboundMessage), `topic` (String) |
| `AgentTask` | record | LLM invocation payload: `systemPrompt`, `assembledInput` |
| `AgentResultParseException` | class | Runtime exception thrown when an agent's LLM output cannot be parsed. Extends `RuntimeException`. |
| `ChannelEventAdapter<E>` | class | Bridge: implements `MessageObserver`, extracts domain events via extractor function, publishes `LevelEvent<E>` to an `EventStreamBus`. Optional channel name filtering. |
| `ChannelEventPublisher<E>` | class | Reverse bridge: subscribes to `EventStreamBus<E>`, dispatches events back to qhorus channels via `MessageDispatcher`. |

### `io.casehub.blocks.channel.summary`

Channel summary integration -- bridges `ContentSummariser` into qhorus `SummaryUpdateHook`.

| Class | Type | What it does |
|-------|------|-------------|
| `ChannelSummariser` | class | CDI-managed `SummaryUpdateHook` implementation. Delegates to injected `ContentSummariser<Message>`. Annotated `@ApplicationScoped`. |
| `HeuristicMessageSummariser` | class | Default (non-LLM) `ContentSummariser<Message>`. Produces heuristic text summaries with participant lists, time periods, topics. Annotated `@DefaultBean @ApplicationScoped` -- replaceable by any `@ApplicationScoped ContentSummariser<Message>`. |
| `NoOpThreadSummaryStore` | class | No-op `@DefaultBean @ApplicationScoped` `ThreadSummaryStore`. `save()` returns the input unchanged, queries return empty. Prevents CDI deployment failures when no real store is on the classpath. Override with a qhorus persistence implementation. |
| `ThreadSummaryObserver` | class | `@ApplicationScoped` push-based observer -- detects DONE/FAILURE messages with correlationId, fetches thread messages via `CrossTenantMessageStore`, delegates to `ContentSummariser<Message>`, writes to `ThreadSummaryStore`. Per-correlationId concurrency guard. Async via `ManagedExecutor`. |

### `io.casehub.blocks.attestation`

Attestation write-path types and lifecycle observer SPI.

| Class | Type | What it does |
|-------|------|-------------|
| `AttestationIntent` | record | Full attestation payload: entryId, subjectId, verdict, confidence, capabilityTag, attestorId, actorType, attestorRole, dimensions, evidence, namespace, causedByEntryId (nullable). |
| `AttestationIntentWriter` | interface | SPI: `void write(AttestationIntent, String tenancyId)`. Implementations MUST honour the provided `entryId`. |
| `NoOpAttestationIntentWriter` | class | No-op `@DefaultBean @ApplicationScoped` `AttestationIntentWriter`. Prevents CDI deployment failures when no real writer is on the classpath. Override with a ledger-backed implementation. |
| `LifecycleAttestationObserver<E>` | interface | `@FunctionalInterface` SPI: `List<AttestationIntent> observe(E event, AttestationContext)`. Domain repos implement per event type. |
| `AttestationContext` | record | Ambient context for observers: tenancyId, caseId, capabilityTag. |

### `io.casehub.blocks.conversation`

Structured conversation protocol -- reusable infrastructure for multi-agent deliberation channels. Extracted from drafthouse. 27 types covering projection, fold state, rendering, epistemic common ground, and convergence detection.

**Core protocol types:**

| Class | Type | What it does |
|-------|------|-------------|
| `ConversationProtocol` | final class | Protocol constants: meta key names (`ENTRY_TYPE`, `ROLE`, `ROUND`, `PRIORITY`, `SCOPE`, `LOCATION`, `POINT_ID`, `SUB_TASK_ID`, `TASK_TYPE`) and infrastructure entry types (`SUB_TASK_REQUEST`, `SUB_TASK_FINDING`, `SUB_TASK_ERROR`, `MEMO`, `FLAG_HUMAN`, `RESTART_CONTEXT`) and statuses (`STATUS_OPEN`, `STATUS_ESCALATED`). |
| `ConversationProjection` | abstract class | Base for conversation-style channel projections. Implements `ChannelProjection<ConversationState>`. Folds channel messages into `ConversationState` by dispatching on metadata entry types. Subclass hooks: `sentinel()`, `isPointInitiator(entryType)`, `statusAfter(entryType)`, `skipEntryTypes()`. |
| `ConversationFold` | final class | Pure state-transition operations on `ConversationState`. Static methods: `createPoint()`, `respondToPoint()`, `reprioritisePoint()`, `flagHuman()`, `addMemo()`, `requestSubTask()`, `completeSubTask()`, `errorSubTask()`. No I/O, no parsing. |
| `ConversationRenderer` | class | Renders `ConversationState` into Markdown. Supports topic grouping, epistemic badges, convergence signal display, obligation chains, reaction counts, sub-task findings. Configurable via `ConversationRendererConfig`. |
| `ConversationRendererConfig` | record | Configuration for `ConversationRenderer`: status emoji, priority labels, entry-type labels, role labels, message-type labels, resolved/escalated status sets, booleans for `groupByTopic`, `showObligationChain`, `showEpistemicStatus`, `showConvergenceSignal`. Has a `Builder`. |

**State types:**

| Class | Type | What it does |
|-------|------|-------------|
| `ConversationState` | record | Top-level immutable state: `points` (Map<String, ConversationPoint>), `humanFlags` (List<FlagEntry>), `memos` (List<RoundMemo>), `subTaskFindings` (Map<String, SubTaskFinding>) |
| `ConversationPoint` | record | Single discussion point: `id`, `topic`, `classification` (PointClassification), `thread` (List<ThreadEntry>), `status` (String) |
| `ThreadEntry` | record | Thread entry: `entryId`, `messageId` (Long), `messageType`, `sender`, `createdAt`, `role`, `round`, `entryType`, `content` |
| `PointClassification` | record | Classifies a point: `priority` (Priority), `scope`, `location` |
| `Priority` | enum | `HIGH`, `MEDIUM`, `LOW` |
| `FlagEntry` | record | Human-escalation flag: `entryId`, `round`, `role`, `content` |
| `RoundMemo` | record | Agent working notes: `role`, `round`, `content` |
| `SubTaskFinding` | record | Sub-task lifecycle: `subTaskId`, `taskType`, `requestedBy`, `pointId`, `finding`, `errorReason`, `status` (TaskStatus) |
| `RenderContext` | record | Optional rendering enrichments: `reactions` (Map), `commonGround` (CommonGroundState), `convergence` (ConvergenceSignal), `progress` (Map). Has `EMPTY` singleton, `withReactions()`, and `withProgress()` factories. |

**Epistemic common ground:**

| Class | Type | What it does |
|-------|------|-------------|
| `CommonGroundAnalyser` | final class | `analyse(ConversationState, EpistemicRule) -> CommonGroundState`. Partitions points into established, pending, and disputed. |
| `CommonGroundState` | record | Maps of `establishedFacts`, `pendingClaims`, `disputedPoints` -- each keyed by point ID to `GroundedFact`. |
| `GroundedFact` | record | Fact from common-ground analysis: `pointId`, `topic`, `status` (EpistemicStatus), `content`, `acknowledgedBy`, `disputedBy`, `round`. |
| `EpistemicRule` | @FunctionalInterface | Classifies a point's epistemic status. Composable via `and()` (most-restrictive) and `or()` (most-permissive). |
| `EpistemicRules` | final class | Built-in rules: `explicitAcknowledgement(minParticipants)`, `tacitAcceptance(windowRounds)`, `commitmentResolution()`. |
| `EpistemicStatus` | enum | `ESTABLISHED`, `PENDING`, `DISPUTED` |
| `ParticipantContext` | record | Aggregated participation data: `allParticipants`, `respondedBy`, `acknowledgedBy`, `completedBy`, `disputedBy`, `failedBy`, `roundsSinceLastActivity`. |

**Convergence detection:**

| Class | Type | What it does |
|-------|------|-------------|
| `ConvergenceAnalyser` | final class | `analyse(ConversationState, CommonGroundState, ConvergencePolicy, recentWindow) -> ConvergenceSignal`. Builds `ConvergenceContext` and delegates to policy. |
| `ConvergenceSignal` | record | Assessment: `state` (ConvergenceState), `confidence`, `reason`. |
| `ConvergenceState` | enum | `PROGRESSING`, `CONVERGING`, `CONSENSUS`, `DEADLOCK`, `DIMINISHING_RETURNS` |
| `ConvergenceContext` | record | Metrics: `totalPoints`, `establishedCount`, `pendingCount`, `disputedCount`, `recentSimilarity`, `messageLengthTrend`, `roundsSinceNewPoint`, `roundsSinceStatusChange`, `recentMessageTypeCounts`. |
| `ConvergencePolicy` | @FunctionalInterface | Strategy for evaluating convergence: `evaluate(state, commonGround, context) -> ConvergenceSignal`. |
| `ConvergencePolicies` | final class | Built-in policies: `structural(similarityThreshold, staleRounds)`, `commonGroundRatio(consensusThreshold, deadlockDisputeRatio)`, `composite(policies...)`. |

### `io.casehub.blocks.conversation.orchestration`

Autonomous multi-agent conversation orchestrator -- composes `ConversationProjection`, `PartitionedObservationService`, pluggable turn policies, and `TerminationCondition` into a self-driving conversation loop. Applications wire it into their `ChannelBackend` with domain-specific context injection only.

**SPIs:**

| Class | Type | What it does |
|-------|------|-------------|
| `TurnPolicy` | interface | Determines which agents respond next. Receives `ConversationState`, `TurnContext`, and the participant list. Returns a list (empty = silence). |
| `PromptAssembler` | @FunctionalInterface | Assembles per-agent prompts from the agent's observation drain and conversation state. Override to inject domain context (document content, selection scope). |
| `ResponseMessageBuilder` | @FunctionalInterface | Converts an agent's `AgentResult` into a `MessageView` the projection can fold. Override for domain-specific entry type determination. |

**Core types:**

| Class | Type | What it does |
|-------|------|-------------|
| `ConversationOrchestrator` | class | Composition root. Constructor takes projection, observation service, turn policy, termination condition, agent invoker, prompt assembler, response builder, response dispatcher, and participant list. `converse(MessageView) -> Uni<ConversationOutcome>` runs the full conversation loop. `terminate()` stops from outside. |
| `ConversationOutcome` | record | Result: `finalState`, `terminationDecision`, `agentResults`, `dispatchCount`, `elapsed`. |
| `AgentParticipant` | record | Agent identity + role + system prompt. `agentId()` delegates to `agentRef.name()`. |
| `TurnContext` | record | Turn-relevant fields: `senderId`, `targetId` (nullable), `entryType`, `metadata`. Extracted from `MessageView` by the orchestrator. |

**Standard turn policies:**

| Class | Type | What it does |
|-------|------|-------------|
| `RoundRobinTurnPolicy` | class | Strict alternation through participants. Stateless -- derives next from `ConversationState`. |
| `AddressedTurnPolicy` | class | Respond when message target matches agent role. Null target = silence. |
| `PointAddressedTurnPolicy` | class | Respond to unresolved OPEN/ACTIVE points not yet responded to by the agent's role. |
| `FreeTurnPolicy` | class | All participants except the sender respond. Pair with `MaxIterationsTermination` as a safety valve. |

**Conversation-specific termination conditions** (all implement `TerminationCondition<ConversationState>`):

| Class | Type | What it does |
|-------|------|-------------|
| `AllAgreedTermination` | class | Complete when all points have a resolved status (configurable set, e.g., AGREED, VERIFIED). |
| `SupervisorTermination` | class | Complete when a supervisor-role agent signals end with a configurable entry type. |
| `ContestedEscalation` | class | Escalate when any DISPUTED point exceeds a dispute-round threshold. |
| `CompositeTermination` | class | Evaluates conditions in order; first non-Continue decision wins. |

**Wiring example (drafthouse debate):**

```java
var participants = List.of(
    new AgentParticipant(reviewerRef, "REV", reviewerSystemPrompt),
    new AgentParticipant(implementorRef, "IMP", implementorSystemPrompt)
);

var orchestrator = new ConversationOrchestrator(
    new DebateChannelProjection(),
    observationService,
    new RoundRobinTurnPolicy(),
    new CompositeTermination(List.of(
        new MaxIterationsTermination<>(20),
        new AllAgreedTermination(Set.of("AGREED", "VERIFIED")),
        new ContestedEscalation(3)
    )),
    debateAgentInvoker,
    new DebatePromptAssembler(documentContent, selectionScope),
    debateResponseBuilder,
    message -> messageService.post(channelId, message),
    participants
);

ConversationOutcome outcome = orchestrator.converse(triggeringMessage)
    .await().indefinitely();
```

### `io.casehub.blocks.normative`

Generic normative conflict resolution — resolves conflicts between competing norm decisions. When multiple norms (e.g., risk classifiers, policy rules) produce contradictory decisions, a `ConflictResolutionStrategy<T>` determines which one wins.

| Class | Type | What it does |
|-------|------|-------------|
| `ConflictResolutionStrategy<T>` | interface | `@FunctionalInterface`: `NormResolution<T> resolve(List<NormDecision<T>>)`. |
| `NormDecision<T>` | record | Decision wrapped with norm metadata: source, priority, specificity, establishedAt. |
| `NormResolution<T>` | record | Resolution outcome: winner, overridden list, reason, method. |
| `NormSpecificity` | enum | UNIVERSAL → DOMAIN → TENANT → CASE_TYPE → INSTANCE. |
| `PriorityResolution<T>` | record | Lowest priority value wins. |
| `SpecificityResolution<T>` | record | Most specific norm wins (lex specialis). |
| `RecencyResolution<T>` | record | Most recently established norm wins (lex posterior). |
| `MostRestrictiveResolution` | record | Typed to `RiskDecision` — GateRequired beats Autonomous. |
| `EscalationResolution<T>` | record | Always escalates when any conflict exists. |

**Usage with oversight:**

```java
var decisions = List.of(
    new NormDecision<>(
        "clinical-classifier", new RiskDecision.GateRequired("AE grade 4", true, null, null, null, null, null),
        1, NormSpecificity.DOMAIN, Instant.now()),
    new NormDecision<>(
        "default-classifier", new RiskDecision.Autonomous(),
        10, NormSpecificity.UNIVERSAL, Instant.now())
);
var resolution = new PriorityResolution<RiskDecision>().resolve(decisions);
// resolution.winner() = clinical-classifier (priority 1 beats 10)
```

### `io.casehub.blocks.negotiation`

Negotiation channel protocol -- reusable `ChannelProjection<NegotiationState>` for proposal/counter-proposal exchange. Uses the PROPOSE MessageType (commissive speech act, qhorus#395). Supports bilateral (two-party alternating) and mediator-coordinated multilateral (N-party with configurable quorum).

| Class | Type | What it does |
|-------|------|-------------|
| `NegotiationProjection` | class | Concrete `ChannelProjection<NegotiationState>`. Constructor: `(Set<String> parties, AcceptancePolicy)`. Dispatches on PROPOSE/DONE/DECLINE. Party set required upfront. `apply()` never throws. |
| `NegotiationFold` | final class | Pure static state transitions: `propose()`, `accept()`, `reject()`, `agree()`, `deadlock()`, `withdraw()`. |
| `NegotiationState` | record | Immutable state: `proposals` (ordered chain), `parties`, `responses` (per-party to active proposal), `outcome`. |
| `Proposal` | record | Individual proposal: proposalId, proposer, content, round, createdAt, status. |
| `Response` | record | Per-party response: party, decision, reason, respondedAt. |
| `AcceptancePolicy` | interface | `@FunctionalInterface`: `boolean isAccepted(NegotiationState)`. |
| `UnanimousAcceptance` | record | All non-proposer parties must accept. |
| `MajorityAcceptance` | record | >50% of non-proposer parties. |
| `ThresholdAcceptance` | record | At least N acceptances. |
| `NegotiationRenderer` | class | Renders state as Markdown: current proposal, responses, pending parties, history. |
| `NegotiationOutcome` | enum | PENDING, AGREED, DEADLOCKED, WITHDRAWN. |
| `ProposalStatus` | enum | ACTIVE, SUPERSEDED, ACCEPTED, REJECTED. |

**Termination conditions** (implement `TerminationCondition<NegotiationState>` from agentic.termination):

| Class | What it does |
|-------|-------------|
| `MaxRoundsTermination` | Complete at max rounds. |
| `AcceptedTermination` | Complete when outcome == AGREED. |
| `TerminalOutcomeTermination` | Complete for AGREED, Failed for DEADLOCKED/WITHDRAWN. |
| `DeadlineTermination` | Complete when latest proposal exceeds deadline. |
| `NegotiationCompositeTermination` | First-non-Continue-wins composition. |

**Consensus Gate pattern** (composition, not a separate type):

A consensus gate (named M-of-N approval) composes directly from negotiation types -- no dedicated `ConsensusProjection` needed:

```java
// Consensus gate: 2-of-3 approval
var consensus = new NegotiationProjection(
    Set.of("chair", "voter-a", "voter-b", "voter-c"),
    new ThresholdAcceptance(2)
);
// Chair posts PROPOSE with the motion, voters respond DONE (approve) or DECLINE (reject).
// ThresholdAcceptance fires AGREED when 2 voters accept.
// For unanimous: use UnanimousAcceptance. For majority: MajorityAcceptance.
```

### `io.casehub.blocks.agentic`

Compositional agentic orchestration framework -- ten sub-packages implementing five SPIs for routing, decomposition, activation, aggregation, and termination, plus execution drivers, accountability listeners, and pre-composed pattern builders.

#### Root types (`agentic`)

| Class | Type | What it does |
|-------|------|-------------|
| `AgentRef` | sealed interface | Extends `ExecutorRef`. Five variants: `WorkerAgent(Worker)`, `ChannelAgent(UUID, ChannelAgentHandler)`, `HumanAgent(WorkItemCreateRequest)`, `ExternalAgent(label, Function)`, `ComposedAgent(ExecutionModel)`. Static factories: `worker()`, `channel()`, `human()`, `external()`, `composed()`. |
| `AgentResult` | record | Execution result: `agent`, `output`, `duration`, `status` (AgentResultStatus enum: SUCCESS, FAILURE, TIMEOUT, DECLINED). Static factories: `success()`, `failure()`, `timeout()`, `declined()`. |
| `RoutingCandidate` | record | Agent candidate for routing: `ref` (AgentRef), `descriptor` (nullable AgentDescriptor). |
| `FailurePolicy` | record | Error handling configuration: `onRoutingFailure` (FAIL/RETRY_BROADER/ESCALATE), `onDeadlock` (FAIL/ESCALATE/RETRY_DIFFERENT), `agentRetry` (AgentRetryPolicy). Inner types: `AgentRetryPolicy` (maxRetries, backoff, strategy, onExhausted), `BackoffStrategy` (FIXED/EXPONENTIAL/EXPONENTIAL_WITH_JITTER). |
| `AgentCardSupport` | final class | Utility: `buildCard(candidate, slot)` and `candidateName(candidate, slot)` for rendering agent descriptions in prompts. |

#### `agentic.routing` -- Routing SPI

| Class | Type | What it does |
|-------|------|-------------|
| `RoutingStrategy<T>` | interface | SPI: `route(RoutingContext<T>) -> Uni<RoutingDecision>` |
| `RoutingContext<T>` | record | Context: `task` (String), `candidates` (List<RoutingCandidate>), `state` (T) |
| `RoutingDecision` | sealed interface | Three outcomes: `Selected(agents, reason)`, `Unresolvable(reason)`, `Escalate(reason)` |
| `Routing` | final class | Factory: `firstMatch(Predicate)`, `roundRobin()`, `sequential()`, `llmSelected(AgentProvider)` |
| `FirstMatchRouting<T>` | class | Selects first candidate matching a predicate |
| `RoundRobinRouting<T>` | class | Cycles through candidates via `AtomicInteger` index |
| `SequentialRouting<T>` | class | Advances in order; returns `Unresolvable` when exhausted |
| `LlmSelectedRouting<T>` | class | LLM-driven agent selection via JSON prompt/response. Handles "done" response. |
| `StageGate` | interface | Stage binding control: `allStagedBindings()`, `activeStagedBindings()` |
| `StageAwareCandidateSupplier` | class | Filters routing candidates by active staged bindings via a `StageGate`. Implements `Supplier<List<RoutingCandidate>>`. |

#### `agentic.decomposition` -- Decomposition SPI

| Class | Type | What it does |
|-------|------|-------------|
| `Decomposition` | final class | Factory: `none()`, `staticTree()`, `goap()`, `hybrid(AgentProvider)` (4 overloads), `heuristic(DecompositionHeuristic)`, `method(Predicate, DecompositionStrategy)`, `sequence(TaskNode...)`, `primitive(AgentRef)`, `compound(name, methods)`, `planned(description, AgentRef)` |
| `Tasks` | final class | Tree construction DSL: `primitive()` (4 overloads), `planned()` (2 overloads), `compound()` (2 overloads), `decompose()` (2 overloads) |
| `PrimitiveTask<T>` | record | Implements `TaskNode.LeafTask<T>`. Fields: `id`, `createdAt`, `description`, `agent` (AgentRef), `precondition` (Predicate<T>), `effect` (Consumer<T>), `outputContract`. |
| `PlannedTask<T>` | record | Implements `TaskNode.LeafTask<T>`. Fields: `id`, `createdAt`, `description`, `agent`, `rationale`, `outputContract`. For LLM-decomposed tasks. |
| `OutputContract` | @FunctionalInterface | Task output validation: `validate(Object)`. Composable via `and()`. Static factories: `nonNull()`, `type(Class)`, `of(Predicate)`. |
| `DecompositionHeuristic<T>` | @FunctionalInterface | Scores decomposition methods: `evaluate(CompoundTask, methods, context) -> Uni<List<ScoredMethod>>` |
| `ScoredMethod<T>` | record | Heuristic output: `method` (DecompositionMethod), `score` (double) |
| `IdentityDecomposition<T>` | class | Pass-through -- wraps single task as-is |
| `StaticDecomposition<T>` | class | Pre-defined task breakdown via `DecompositionMethod` guard evaluation; pre-filters methods whose `estimatedCost`/`estimatedDuration` exceed `context.constraints()` before guard evaluation |
| `ForwardReasoningDecomposition<T>` | class | SHOP-style forward reasoning -- applies `PrimitiveTask.effect()` to projected state copy during planning |
| `LlmDecomposition<T>` | class | LLM-driven decomposition. Recursive multi-level planning via `maxDepth`. Parses JSON response to create `PlannedTask` or `CompoundTask` nodes. |
| `HybridDecomposition<T>` | class | Static-first with LLM fallback. Passes `staticFailureHint` from failed static method to LLM context. |
| `GoalOrientedDecomposition<T>` | class | GOAP backward-chaining: matches goal types to agent capabilities via preconditions/effects, builds dependency DAG. Returns `"goap"` from `id()`. |
| `HeuristicDecomposition<T>` | class | Ranked method selection via pluggable `DecompositionHeuristic<T>` with backtracking on `NoMethodMatchedException`. Returns `"heuristic"` from `id()`. |
| `LlmDecompositionHeuristic<T>` | class | LLM-as-heuristic: asks LLM to score each decomposition method 0.0-1.0. Falls back to equal scores on failure. |
| `StructuralCostHeuristic<T>` | class | Structural cost estimation: scores by negative estimated cost. Leaf=1, compound=min of methods, opaque=configurable default. |
| `CompositeHeuristic<T>` | class | Weighted combination of multiple heuristics with min-max normalisation. Inner type: `WeightedHeuristic<T>(heuristic, weight)`. |
| `NoMethodMatchedException` | class | Extends `IllegalStateException`. Fields: `taskName`, `methodCount`. Thrown when no decomposition method's guard matches. |
| `AgenticDecompositionContext<T>` | record | Implements `DecompositionContext<T>`. Fields: `state`, `agents`, `depth`, `staticFailureHint`, `subtaskDescription`, `parentGoal`, `siblingNames`, `decomposer`, `planningConstraints`. Overrides `constraints()` to return `planningConstraints` (defaults to `unconstrained()` when null). |
| `GoapDecompositionContext<T>` | record | Implements `DecompositionContext<T>`. Fields: `state`, `agents`, `depth`, `goalTypes`, `availableTypes`. |

#### `agentic.activation` -- Activation SPI

| Class | Type | What it does |
|-------|------|-------------|
| `ActivationRule<T>` | interface | SPI: `shouldActivate(ActivationContext<T>) -> Uni<Boolean>` |
| `ActivationContext<T>` | record | Context: `event`, `state` (T), `agent` (AgentRef), `activationCount`, `lastAggregationResult` (Optional), `consecutiveIdleActivations` |
| `Activation` | final class | Factory: `onExplicitDispatch()`, `maxIterations(int)` |
| `OnExplicitDispatch<T>` | class | Always activates (returns true) |
| `MaxIterationsGuard<T>` | class | Activates while `activationCount < maxIterations` |

#### `agentic.aggregation` -- Aggregation SPI

| Class | Type | What it does |
|-------|------|-------------|
| `AggregationStrategy<T>` | interface | SPI: `aggregate(List<AgentResult>, AggregationContext<T>) -> Uni<AggregationResult>` |
| `AggregationContext<T>` | record | Context: `state` (T) |
| `AggregationResult` | sealed interface | Three outcomes: `Resolved(value)`, `Partial(collected, remaining)`, `Deadlocked(reason)` |
| `Aggregation` | final class | Factory: `passThrough()`, `collectAll()`, `majorityVote()` |
| `PassThrough<T>` | class | Returns last result's output as `Resolved` |
| `CollectAll<T>` | class | Collects all results into `Resolved(List)` |
| `MajorityVote<T>` | class | Counts output occurrences; returns winner or `Deadlocked` on tie |

#### `agentic.termination` -- Termination SPI

| Class | Type | What it does |
|-------|------|-------------|
| `TerminationCondition<T>` | interface | SPI: `evaluate(TerminationContext<T>) -> TerminationDecision`. Composable via `or(other)` (either can terminate) and `and(other)` (both must agree). Priority-aware: Escalate > Failed > Complete > Continue. |
| `TerminationContext<T>` | record | Context: `state` (T), `iterationCount`, `elapsed` (Duration), `results` (List<AgentResult>) |
| `TerminationDecision` | sealed interface | Four outcomes: `Continue` (singleton INSTANCE), `Complete(result)`, `Failed(reason)`, `Escalate(reason)` |
| `Termination` | final class | Factory: `goalReached(Predicate)`, `maxIterations(int)` |
| `GoalReached<T>` | class | Completes when predicate passes on state |
| `MaxIterationsTermination<T>` | class | Completes when `iterationCount >= maxIterations` |
| `JudgeConvergence<T>` | class | Invokes a judge agent with accumulated results; convergence predicate maps result to continue/complete. Safety cap at `maxIterations`. |
| `ConvergenceTermination<T>` | class | Uses `CommonGroundAnalyser` and `ConvergenceAnalyser` from the conversation package. Terminates on CONSENSUS (Complete), DEADLOCK (Escalate), or DIMINISHING_RETURNS (Complete) when confidence exceeds threshold. |

#### `agentic.plan`

| Class | Type | What it does |
|-------|------|-------------|
| `ExecutionPlan<T>` | record | DAG expressing task dependencies. Validated: no cycles, all references exist. Factory methods: `singleton()`, `sequence()`, `parallel()`, `fromList()`. |

#### `agentic.model` -- Execution Model

| Class | Type | What it does |
|-------|------|-------------|
| `ExecutionModel<T>` | record | Wires the five SPIs together: `routing`, `decomposition`, `activation`, `aggregation`, `termination`, plus `candidateSupplier`, `failurePolicy`, `listeners`, `task`, `patternType`. |
| `ExecutionDriver<T>` | interface | Runs an `ExecutionModel`: `execute(model, state) -> Uni<ExecutionResult>`, `cancel()`, `state()` |
| `ExecutionResult` | sealed interface | Four outcomes: `Completed(result)`, `Failed(reason, cause)`, `Escalated(reason)`, `Cancelled()` |
| `ExecutionState` | sealed interface | Seven states: `Idle`, `Running(iteration)`, `WaitingForAgent(agent)`, `WaitingForEvent`, `Complete`, `Faulted`, `Cancelled` |
| `ExecutionEventListener` | interface | Callback SPI with default no-ops: `onRoutingDecision()`, `onActivation()`, `onAgentDispatched()`, `onAgentResult()`, `onAggregation()`, `onTermination()`, `onStateTransition()`, `onFailure()`, `onExecutionStart()`, `onExecutionComplete()`. Static method: `agentName(AgentRef)`. |
| `AgentInvoker<T>` | @FunctionalInterface | Agent dispatch: `invoke(AgentRef, T) -> Uni<AgentResult>`. Default: `withFallback(AgentInvoker)`. Static `defaultInvoker()` handles ExternalAgent/ComposedAgent, returns failure for others. |
| `ExecutionBackend<T>` | @FunctionalInterface | Top-level entry: `execute(ExecutionModel<T>, T) -> Uni<ExecutionResult>`, `cancel()` (default no-op). Static: `reactive()`, `reactive(AgentInvoker)` — return `CancellableBackend` wrapping `OrchestratedDriver`. |
| `OrchestratedDriver<T>` | class | Imperative while-loop execution driver |
| `ChoreographedDriver<T>` | class | Event-reactive execution driver |
| `PatternType` | enum | `SEQUENCE`, `PARALLEL`, `LOOP`, `CONDITIONAL`, `SUPERVISOR`, `DEBATE`, `VOTING`, `HTN`. Method: `isWorkflowShaped()` (true for first four). |

#### `agentic.channel` -- Inter-Agent Channels + Supervisor Observation

| Class | Type | What it does |
|-------|------|-------------|
| `ChannelBinding` | record | Binds a channel to execution: `channelId` (UUID), `semantic` (ChannelSemantic). |
| `ChannelConfig` | record | Channel setup: `channelManager`, `messageDispatcher`, `semantic`, `protocols`. Factory: `of(channelManager, dispatcher, semantic)`. |
| `ChannelExecutionStrategy<T>` | sealed interface | Execution strategy for channel-based agent communication. Three variants: `Conversation` (wraps ConversationOrchestrator), `FanIn` (parallel dispatch, sequential collection), `Barrier` (parallel dispatch, barrier sync). |
| `ChannelObserver<S>` | class | Supervisor observation API. Implements both `MessageObserver` (qhorus CDI-based dispatch) and `EventSource` (ChoreographedDriver wake-up). Folds channel messages through `ChannelProjection<S>` via `AtomicReference.updateAndGet()`. `currentState()` returns the projected state. `reset()` clears state between executions. `terminateWhen(Predicate<S>)` and `asTermination(Function<S, TerminationDecision>)` create `TerminationCondition<T>` instances that read from the projection. Factory: `of(projection, channelName)`. Builder: `builder(projection).channel(name1).channel(name2).build()` for multi-channel observation. |
| `ChannelTeardownListener` | class | Cleans up channels on execution completion. |

#### `agentic.listener` -- Accountability Listeners

| Class | Type | What it does |
|-------|------|-------------|
| `OrchestrationEventType` | enum | Event categories: `EXECUTION_STARTED`, `ROUTING_DECISION`, `ACTIVATION_EVALUATED`, `AGENT_DISPATCHED`, `AGENT_RESULT`, `AGENT_FAILED`, `AGGREGATION_COMPLETED`, `TERMINATION_EVALUATED`, `EXECUTION_COMPLETED` |
| `EventLogListener` | class | Operational audit via `EventSink`. Inner type: `EventSink` (@FunctionalInterface: `record(type, Map<String, Object>)`) |
| `LedgerExecutionListener` | class | EU AI Act Art.12 compliance-grade audit via `LedgerSink`. Inner type: `LedgerSink` (@FunctionalInterface: `record(type, actorId, actorRole, Map<String, Object>)`) |
| `MetricsListener` | class | OpenTelemetry metrics via `Meter`: agent duration histogram, routing/activation counters, failure counter, execution duration/iterations histograms |

#### `agentic.pattern` -- Pattern DSL

| Class | Type | What it does |
|-------|------|-------------|
| `Patterns` | final class | Entry point: `supervisor()`, `supervisor(AgentProvider)`, `sequence()`, `loop()`, `parallel()`, `voting()`, `debate()`, `conditional()`, `htn()` |
| `SupervisorBuilder<T>` | class | LLM-selected routing (when AgentProvider given), default MaxIterationsTermination(10). Method: `stateRenderer(Function)`. |
| `SequenceBuilder<T>` | class | SequentialRouting, terminates after `agentCount` iterations |
| `LoopBuilder<T>` | class | RoundRobinRouting, configurable `maxIterations(int)` and `exitCondition(Predicate)` |
| `ParallelBuilder<T>` | class | Routes to all candidates simultaneously, CollectAll aggregation |
| `VotingBuilder<T>` | class | All candidates vote, MajorityVote aggregation. Method: `evaluators(AgentRef...)`, `strategy(AggregationStrategy)`. |
| `DebateBuilder<T>` | class | RoundRobinRouting, configurable `maxRounds(int)`. Methods: `debaters(AgentRef...)`, `judge(AgentRef)`, `convergence(TerminationCondition)` (mutually exclusive with judge). |
| `ConditionalBuilder<T>` | class | FirstMatchRouting with `when(Predicate, AgentRef)` for conditional dispatch |
| `HtnBuilder<T>` | class | HTN execution: `rootTask(TaskNode)`, flattens HTN tree via decomposition, builds dynamic ExecutionModel |

All builders extend `AbstractPatternBuilder` and provide: `route()`, `decompose()`, `activate()`, `aggregate()`, `terminate()`, `listener()`, `task()`, `backend()`, `build()` (returns ExecutionModel), `execute(T)` (builds and runs).

### `io.casehub.blocks.routing`

Trust routing audit types -- compliance records for trust-weighted routing decisions.

| Class | Type | What it does |
|-------|------|-------------|
| `RoutingDecisionRecord` | record | Compliance audit record: `capabilityTag`, `workerId`, `trustScoreAtRouting` (nullable Double), `thresholdApplied`, `evidenceEntryId` (UUID) |
| `TrustRoutingRequirement` | record | Compliance evidence wrapper: `requirementId`, `citation`, `mechanism`, `status` (RequirementStatus), `decisions` (List<RoutingDecisionRecord>) |
| `RequirementStatus` | enum | `CLOSED`, `PARTIAL`, `BREACHED`, `GAP` |

### `io.casehub.blocks.routing.agent`

AI-powered `AgentRoutingStrategy` implementations for the engine's routing pipeline. Strategies are selected by name via `StrategyResolver` (engine#634). Optional trust classification via `Instance<T>` -- activates when engine-ledger is on the consumer's classpath.

| Class | Type | What it does |
|-------|------|-------------|
| `LlmAgentRoutingStrategy` | class (@ApplicationScoped) | Strategy id: `"llm"`. LLM reasoning about which candidate best fits the task. Composable prompt enrichment via `RoutingPromptAssembler` with configurable char budget (`casehub.blocks.routing.llm.prompt-budget-chars`, default: unlimited). Trust filtering via `RoutingSupport.applyTrustFilter()`. |
| `CbrAgentRoutingStrategy` | class (@ApplicationScoped) | Strategy id: `"cbr"`. Case-based evidence with similarity-weighted scoring via `ExperienceAnalyser.workerSuccessRates()`. Falls back to `AgentGraphQuery.topAgentsByOutcome()`, then `TrustCandidateClassifier.decide`. |
| `PlanCompositionAnalyser` | class (@ApplicationScoped) | `RoutingSignalProvider` (id: `"plan-composition"`). Scores candidates based on case-level outcomes in multi-step plans (planTrace.size() >= 2). |
| `PredecessorAnalyser` | class (@ApplicationScoped) | `RoutingSignalProvider` (id: `"predecessor"`). Scores candidates based on immediate predecessor context in historical plan traces. |
| `CoordinationSignalProvider` | class (@ApplicationScoped) | `RoutingSignalProvider` (id: `"coordination"`). Scores candidates by historical team composition outcomes with adaptation-guided retrieval. |
| `DispositionAwareRouting` | class (@ApplicationScoped) | `RoutingSignalProvider` (id: `"disposition"`). Personality/disposition match scoring via `AgentDisposition` and `DispositionProfile`. Extracts desired profile from case context JSON. |
| `CbrRoutingPromptSection` | class (@ApplicationScoped) | `RoutingPromptSection` -- renders historical CBR outcomes per eligible agent for LLM routing prompts. |
| `CbrOutcomeWeights` | interface (SPI) | Step-level outcome weights: `Map<RoutingOutcome, Double> weights()`. |
| `CbrCaseOutcomeWeights` | interface (SPI) | Case-level outcome weights: `Map<String, Double> weights()`. String keys for domain-dependent outcomes. |
| `CoordinationOutcomeWeights` | interface (SPI) | Case-level outcome weights for coordination scoring. Same shape as `CbrCaseOutcomeWeights`. |
| `DefaultCbrOutcomeWeights` | class (@DefaultBean) | Default step-level weights: SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, FAILURE=0.0 |
| `DefaultCbrCaseOutcomeWeights` | class (@DefaultBean) | Default case-level weights: COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0 |
| `DefaultCoordinationOutcomeWeights` | class (@DefaultBean) | Default coordination weights: COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0 |
| `DispositionProfile` | record | Desired disposition for routing: `desired` (Map<DispositionAxis, String>), `weights` (Map<DispositionAxis, Double>). Method: `weight(axis)` defaults to 1.0. |

### `io.casehub.blocks.summarisation`

Layered event summarisation framework -- temporal event accumulation with configurable window policies and pluggable summarisation strategies. Pure Java, zero CDI/Quarkus dependencies.

| Class | Type | What it does |
|-------|------|-------------|
| `EventLevel` | record | Named level in the temporal hierarchy: `name`, `ordinal` |
| `LevelEvent<E>` | record | Typed event at a specific level: `payload`, `timestamp`, `level` |
| `WindowPolicy` | record | Window boundaries: `maxAge` (ms), `maxCount`. At least one must be positive. Factory methods: `ofCount(int)`, `ofAge(long)`, `of(long, int)`. |
| `EventAccumulator<E>` | class | Thread-safe event buffer with `collect()`, `shouldEmit()`, `drain()`, `drainIfReady(now)`, `clear()`, `size()`. All methods synchronized. |
| `EventStreamBus<E>` | class | Predicate-based pub/sub. `subscribe(Predicate, Consumer)`, `publish(LevelEvent)`. `clearSubscriptions()`. Backed by `CopyOnWriteArrayList`. Synchronous dispatch. |
| `Summariser<IN, OUT>` | @FunctionalInterface | Core contract: `summarise(List<LevelEvent<IN>>) -> CompletionStage<List<OUT>>`. Static: `ofSync(SyncSummariser)`. Inner type: `SyncSummariser<IN, OUT>` (@FunctionalInterface). |
| `Compactor<E>` | @FunctionalInterface | Pre-processing SPI: `compact(List<LevelEvent<E>>) -> List<LevelEvent<E>>`. Runs between drain and summarise for merge/deduplicate/filter. |
| `SummarisationRunner<IN, OUT>` | class | Wires accumulator -> optional compactor -> summariser -> output bus. Tick-driven via `tick(now)`. `flush()` for unconditional drain at shutdown. |
| `KeyedAccumulator<K, E>` | class | Groups events by key, emits each group on completion predicate or stale timeout. `collect()`, `drain(now)`, `drainAll()`. |
| `KeyedSummarisationRunner<K, IN, OUT>` | class | Grouped variant: per-key summarisation with independent failure recovery. `collect()`, `tick(now)`, `flush()`. Optional `onFailure` callback. |
| `ContentSummariser<T>` | @FunctionalInterface | Higher-level SPI: `summarise(List<T>, @Nullable SummaryResult) -> CompletionStage<SummaryResult>`. Decoupled from pipeline event model. |
| `ContentSummariserToSummariser<T>` | class | Adapter: wraps `ContentSummariser<T>` to satisfy `Summariser<T, String>`. Strips `LevelEvent` wrappers. |
| `TieredContentSummariser<T>` | class | Routes to one of three `ContentSummariser<T>` delegates (small/medium/large) based on item count thresholds. |
| `VerbatimContentSummariser<T>` | class | Renders each item as a bullet point using a `Function<T, String>` renderer. Prepends previous summary. Annotates with `tier=verbatim`. No LLM. |

### `io.casehub.blocks.summarisation.llm`

| Class | Type | What it does |
|-------|------|-------------|
| `LlmContentSummariser<T>` | class | LLM-backed `ContentSummariser`. Uses `AgentProvider` with EDIT or APPEND prompt mode. Renders items via `Function<T, String>`, optional preamble. Annotates with `tier=synthesised`. |

### `io.casehub.blocks.summarisation.observation`

Observation accumulator -- tiered, demand-driven rendering for LLM agent prompts.

| Class | Type | What it does |
|-------|------|-------------|
| `ObservationAccumulator<E>` | class | Thread-safe buffer. `collect(LevelEvent)`, `drainObservation(now) -> CompletionStage<ObservationResult>`. Tracks last drain timestamp. |
| `ObservationResult` | record | Drain output: `renderedText`, `chunks` (List<ObservationChunk>), `eventCount`, `timeSinceLastDrain`, `tier` (ObservationTier). Static: `empty(timeSinceLastDrain)`. |
| `TieredObservationRenderer<E>` | class | Three-tier renderer. Below verbatimThreshold: individual events. Between thresholds: grouped by key. Above: summarised via `Summariser`. `withHeaderFormatter()` for custom headers. |
| `PartitionedObservationService<E, K>` | class | Per-observer, per-partition accumulators. Events routed via `VisibilityPolicy`. `addObserver()`, `publishEvent()`, `drain(observerId, currentPartition, now) -> PartitionedDrain<K>`. Uses `ConcurrentHashMap`. |

### `io.casehub.blocks.summarisation.observation.affordance`

Affordance grounding -- per-entity observation rendering for LLM agents.

| Class | Type | What it does |
|-------|------|-------------|
| `ObservableEntity` | record | Entity to observe: `id`, `displayName`, `description` (nullable), `affordances` (List<Affordance>) |
| `Affordance` | record | Action on an entity: `actionType`, `label` (nullable), `requiredItem` (nullable), `acceptsItems` (List<String>) |
| `ActionDescriptor` | record | Action type metadata: `actionType`, `description`, `parameterFormat` (nullable) |
| `ObservationSection` | sealed interface | Document structure: `EntityGroup(header, emptyMessage, entities)`, `TextBlock(header, content)`, `ItemList(header, emptyMessage, items)`. Static factories: `entities()`, `text()`, `items()`. |
| `AffordanceRenderer` | class | Renders `ObservableEntity` lists and `ObservationSection` trees into plain text. Methods: `renderEntities()`, `renderObservation()`, `renderActionVocabulary()`, `withHeaderFormatter()`. |

## Key Integration Patterns

**Summarisation Pattern A** (SummarisationRunner pipeline): sync heuristics, microsecond latency. Wire accumulator -> optional compactor -> summariser -> output bus. Use `flush()` at shutdown for unconditional drain.

**Summarisation Pattern B** (direct EventAccumulator): async LLM dispatch, caller manages the accumulator lifecycle.

**Summarisation Pattern C** (TieredContentSummariser): volume-adaptive summarisation -- verbatim for small batches, grouped for medium, LLM-synthesised for large.

**Channel bridges**: `ChannelEventAdapter` (channel -> event bus) and `ChannelEventPublisher` (event bus -> channel) provide bidirectional integration between qhorus channels and the summarisation pipeline.

**Channel summary hook**: `ChannelSummariser` bridges `ContentSummariser<Message>` into qhorus's `SummaryUpdateHook`. Default: `HeuristicMessageSummariser`. Replace with an `@ApplicationScoped ContentSummariser<Message>` for LLM-powered summaries (see `LlmContentSummariser`).

**Observation pipeline**: `ObservationAccumulator` -> `TieredObservationRenderer` for demand-driven agent context. `PartitionedObservationService` for multi-observer scenarios.

**Pattern DSL**: `Patterns.supervisor().agents(a, b, c).task("route claim").execute(state)` for quick multi-agent setups. HTN pattern via `Patterns.htn().rootTask(compound(...)).execute(state)`.

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
- Does NOT own oversight gate lifecycle or risk classification -- those are in engine-api and openclaw

## Dependencies

**Compile:** `casehub-qhorus-api`, `casehub-work-api`, `casehub-engine-api`, `casehub-eidos-api`, `casehub-worker-api`, `org.jspecify:jspecify`

**Provided:** `io.smallrye.reactive:mutiny`, `casehub-platform-agent-api`, `casehub-platform-api`, `casehub-engine-ledger`, `casehub-ledger-api`, `casehub-neocortex-memory-api`, `io.opentelemetry:opentelemetry-api`

**Test:** `casehub-qhorus`, `casehub-qhorus-testing`, `casehub-engine`, `casehub-engine-testing`, `quarkus-junit`, `assertj`, `mockito`, `awaitility`, `io.opentelemetry:opentelemetry-sdk-testing`
