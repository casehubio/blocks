# ARC42STORIES — casehub-blocks

## Layer Taxonomy

| § | Chapter | Status | Notes |
|---|---------|--------|-------|
| 1 | Introduction and Goals | ✅ initial (#2) | |
| 2 | Constraints | 🔲 pending (TBD) | |
| 3 | Context and Scope | 🔲 pending (TBD) | |
| 4 | Solution Strategy | ✅ initial (#2) | Single module, all foundation deps |
| 5 | Building Block View | ✅ initial (#2) | `channel`, `agentic`, `summarisation` packages |
| 6 | Runtime View | 🔲 pending (TBD) | |
| 7 | Deployment View | 🔲 pending (TBD) | N/A — library, no deployment |
| 8 | Crosscutting Concepts | 🔲 pending (TBD) | |
| 9 | Architecture Decisions | ✅ initial (#2) | Sentinel parameterised, not hardcoded |
| 10 | Quality Requirements | 🔲 pending (TBD) | |
| 11 | Risks and Technical Debt | 🔲 pending (TBD) | |
| 12 | Glossary | 🔲 pending (TBD) | |

---

## §1 Introduction and Goals

Curated collection of reusable building blocks composed from CaseHub foundation primitives (qhorus, engine, work). App developers pick the blocks they need to assemble their applications.

Not infrastructure, not a framework, not a tool library. Blocks are composed workflows and interaction patterns that sit between the foundation layer and application logic.

**Epic:** casehubio/parent#310

---

## §4 Solution Strategy

Single Maven module. Depends on `casehub-qhorus-api` + `casehub-work-api` + `casehub-engine-api`. No tiered sub-modules — blocks can freely compose from all three foundations. Every CaseHub app already has the full foundation stack, so transitive deps are not a concern.

---

## §5 Building Block View

### Package: `io.casehub.blocks.channel`

Channel utility blocks — foundational patterns for LLM-mediated channel communication.

| Block | What it does | Extracted from |
|-------|-------------|---------------|
| `ChannelMessageMeta` | Sentinel-prefixed key=value metadata headers in message bodies. Apps choose their own sentinel prefix — the utility handles the format. | DraftHouse `DebateProtocol` |
| `ContextTracker` | Incremental LLM context window usage tracking with threshold warnings. Thread-safe via atomic counters. | DraftHouse `ContextTracker` |
| `ContextSnapshot` | Immutable record of context state at a point in time. | DraftHouse `ContextSnapshot` |
| `BoundedProjectionDecorator<S>` | Generic decorator wrapping any qhorus `ChannelProjection<S>` — skips messages where an extracted value exceeds a bound. Consumer supplies the extraction function. | DraftHouse `RoundBoundedProjection` |
| `ChannelAgentHandler` | SPI for sub-task handlers: `handles()` predicate, `prepareTask()` assembles LLM input, `buildResponse()` builds qhorus MessageDispatch from LLM output. First-match routing. | DraftHouse `ChannelAgentHandler` |
| `ChannelAgentDispatcher` | First-match handler routing + agent invocation. Agent provider via `Function<AgentTask, String>`, message sink via `Consumer<MessageDispatch>`. Subclass `onError()` for app-specific error encoding. CDI-friendly (protected no-args constructor). | DraftHouse `ChannelAgentDispatcher` |
| `ChannelAgentRequest` | Sub-task trigger record: channelId, correlationId, OutboundMessage. | DraftHouse `ChannelAgentRequest` |
| `AgentTask` | LLM input record: systemPrompt, assembledInput. | DraftHouse `AgentTask` |
| `AgentResultParseException` | Unchecked exception signalling handler parse failure — dispatcher catches and routes to `onError()`. | DraftHouse `AgentResultParseException` |
| `ChannelEventAdapter<E>` | Direction 1 bridge: implements `MessageObserver`, extracts domain events via extractor function, publishes `LevelEvent<E>` to `EventStreamBus`. Null return filters. Extractor exceptions caught and logged. | #40 |
| `ChannelEventPublisher<E>` | Direction 2 bridge: subscribes to `EventStreamBus<E>`, converts events to `MessageDispatch` via builder function, dispatches via `MessageDispatcher`. Best-effort — never propagates. | #40 |

### Package: `io.casehub.blocks.agentic`

Compositional agentic orchestration framework — five SPIs for agent selection, task decomposition, activation, result aggregation, and termination, with execution drivers and pre-composed pattern builders.

| Sub-package | What it contains |
|-------------|-----------------|
| `io.casehub.blocks.agentic` | Foundation types: `AgentRef` (sealed, 5 variants), `AgentResult`, `RoutingCandidate`, `FailurePolicy` |
| `io.casehub.blocks.agentic.routing` | Routing SPI: `RoutingStrategy<T>`, `RoutingDecision` (sealed), `FirstMatchRouting`, `RoundRobinRouting`, `SequentialRouting` |
| `io.casehub.blocks.agentic.decomposition` | Decomposition SPI: `DecompositionStrategy<T>`, `TaskNode` (sealed), `IdentityDecomposition`, `StaticDecomposition`, `LlmDecomposition`, `HybridDecomposition`, `NoMethodMatchedException` |
| `io.casehub.blocks.agentic.activation` | Activation SPI: `ActivationRule<T>`, `OnExplicitDispatch`, `MaxIterationsGuard` |
| `io.casehub.blocks.agentic.aggregation` | Aggregation SPI: `AggregationStrategy<T>`, `AggregationResult` (sealed), `PassThrough`, `CollectAll`, `MajorityVote` |
| `io.casehub.blocks.agentic.termination` | Termination SPI: `TerminationCondition<T>`, `TerminationDecision` (sealed), `GoalReached`, `MaxIterationsTermination` |
| `io.casehub.blocks.agentic.model` | Execution model: `ExecutionModel<T>`, `ExecutionDriver<T>`, `OrchestratedDriver`, `ChoreographedDriver`, `ExecutionResult` (sealed), `ExecutionState` (sealed), `ExecutionEventListener` |
| `io.casehub.blocks.agentic.pattern` | Pattern DSL: `Patterns` entry point, 8 builders (Supervisor, Sequence, Loop, Parallel, Voting, Debate, Conditional, HTN) |

**Implementation epic:** casehubio/blocks#595

### Package: `io.casehub.blocks.summarisation`

Temporal abstraction framework for summarising high-frequency event streams into progressively higher-level abstractions. Pure Java, zero CDI/Quarkus dependencies.

| Block | What it does | Extracted from |
|-------|-------------|---------------|
| `EventLevel` | Record identifying a level in the hierarchy: `(String name, int ordinal)` | quarkmind `EventLevel` |
| `LevelEvent<E>` | Record wrapping a typed event at a specific level: `(E payload, long timestamp, EventLevel level)` | quarkmind `LevelEvent` |
| `WindowPolicy` | Record defining dual-trigger windowing: `(long maxAge, int maxCount)`. Validates: both >= 0, at least one positive. | quarkmind `WindowPolicy` |
| `EventAccumulator<E>` | Thread-safe event buffer with windowing logic. `collect()`, `shouldEmit(now)`, `drain()`, `clear()`, `size()`. All public methods synchronized. | quarkmind `EventAccumulator` |
| `EventStreamBus<E>` | Predicate-based pub/sub for level events. `subscribe(Predicate, Consumer)`, `publish(LevelEvent)`, `clear()`. CopyOnWriteArrayList-backed — concurrent publish+subscribe safe. | quarkmind `EventStreamBus` |
| `Summariser<IN, OUT>` | `@FunctionalInterface` for summarisation logic: `CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>>)`. `ofSync()` factory for deterministic implementations. | quarkmind `Summariser` |
| `SummarisationRunner<IN, OUT>` | Wires accumulator → summariser → output bus. `collect()`, `tick()` (returns `CompletionStage<Void>`), `clear()`, `size()`. | quarkmind `SummarisationRunner` |
| `KeyedAccumulator<K, E>` | Groups events by key, emits each group on completion predicate or stale timeout. Thread-safe. Clock-from-last-event staleness. `collect()`, `drain(long)`, `clear()`, `groupCount()`, `eventCount()`. | #40 |
| `KeyedSummarisationRunner<K, IN, OUT>` | Wires `KeyedAccumulator` → `Summariser` → output bus. Per-group at-most-once error semantics. `collect()`, `tick(long)`, `clear()`, `groupCount()`, `eventCount()`. | #40 |

Two integration patterns:
- **Pattern A** (SummarisationRunner pipeline): sync heuristics, microsecond latency. Used for rule-based summarisation where speed is critical.
- **Pattern B** (direct EventAccumulator): async LLM dispatch, caller manages execution. Used when summarisation requires LLM reasoning and latency tolerance is higher.
- `KeyedSummarisationRunner` is the grouped counterpart to `SummarisationRunner` — same compositional role, groups by key instead of flat windowing.

**Implementation issues:** casehubio/blocks#27, casehubio/blocks#40

### Planned packages (per parent#310)

| Package | What it will contain | Priority |
|---------|---------------------|----------|
| `io.casehub.blocks.conversation` | Structured conversation protocol — round-based multi-agent deliberation with point lifecycle, human escalation, sub-task dispatch | P5 |
| `io.casehub.blocks.gate` | Oversight gate lifecycle — risk classification → approval gate → commitment → decision | P6 (from openclaw) |

---

## §9 Architecture Decisions

### Sentinel prefix is parameterised, not hardcoded

`ChannelMessageMeta` takes the sentinel as a parameter in every method rather than hardcoding a default. Each app defines its own prefix (DraftHouse uses `DHMETA:`). This avoids coupling blocks to any one app's protocol while keeping the encoding scheme standard.

### BoundedProjectionDecorator uses a value extractor function

Rather than hardcoding "round number" as the bound concept, the decorator takes a `ToIntFunction<MessageView>` that extracts the bound value from each message. DraftHouse passes `msg -> DebateProtocol.parseRound(parseMeta(msg.content()))`. Other apps can bound on sequence number, timestamp, or any integer field.

### ChannelAgentDispatcher uses Consumer/Function, not MessageService

The dispatcher takes `Function<AgentTask, String>` for agent invocation and `Consumer<MessageDispatch>` for message posting — not the qhorus runtime `MessageService` directly. This keeps blocks at the API dependency level (qhorus-api only, not qhorus runtime). Apps pass `messageService::dispatch` and `agentProvider::analyse` as method references. Error dispatch is a protected `onError()` method that apps override to encode errors in their protocol format.
