# blocks Workspace
**Name:** casehub-blocks

**Physical path:** `/Users/mdproctor/claude/casehub/blocks/CLAUDE.md`
**Symlinked at:** `/Users/mdproctor/claude/public/casehub/blocks/CLAUDE.md`
**Project repo:** `/Users/mdproctor/claude/casehub/blocks`
**Workspace:** `/Users/mdproctor/claude/public/casehub/blocks`
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/blocks` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/blocks`) — methodology artifacts: handover, blog (staging before publish), plans, snapshots
- **Project repo** (`/Users/mdproctor/claude/casehub/blocks`) — source code, ADRs (`docs/adr/`), specs

Never rely on CWD for git operations — the session may have started in either repo. Always use explicit paths:
```bash
git -C /Users/mdproctor/claude/public/casehub/blocks ...   # workspace artifacts
git -C /Users/mdproctor/claude/casehub/blocks ...           # project artifacts
```

## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` |
| blog       | project     | lands in `docs/blog/` — promoted at work end |
| design     | project     | journal file lives in workspace design/; DESIGN.md merge target is project docs/DESIGN.md |
| snapshots  | workspace   | |
| specs      | project     | lands in docs/specs/ |
| plans      | workspace   | |
| handover   | workspace   | |

---

# CaseHub Blocks

## Project Type

type: java

## Repository Role

Reusable building blocks for CaseHub applications — composed from qhorus, engine, and work primitives. Foundation-adjacent library (sits between foundation and application tier). Single module, single artifact: `casehub-blocks`.

**Peer repos (each has its own Claude session — do not commit to these):**
platform, eidos, ledger, connectors, iot, work, worker, qhorus, pages, engine, claudony, openclaw, neural-text, devtown, aml, clinical, drafthouse, life, quarkmind, flow, soc, fsitrading, ras, ops, workers, desiredstate

## Platform Docs
- [Platform Index](https://raw.githubusercontent.com/casehubio/parent/main/docs/INDEX.md) — discovery index (start here)
- [Building Platform](https://raw.githubusercontent.com/casehubio/parent/main/docs/guides/building-platform.md) — platform contributor guide

## Repo Guide

This repo owns its own documentation, synced to parent via CI:
- `docs/guides/consumer-guide.md` — for app builders: modules, APIs, quick start
- `docs/guides/contributor-guide.md` — for platform builders: architecture, SPIs, internals

Update the relevant guide in the same session when implementation changes modules, SPIs, or public APIs. Do not defer — drift compounds.

Read `docs/guides/consumer-guide.md` for app-level work. Only read `docs/guides/contributor-guide.md` when modifying this repo's internals or extension points.

## Build Commands

```bash
mvn --batch-mode install
mvn --batch-mode test
```

## Testing

No Quarkus runtime — plain JUnit 5 tests with Mockito. No CDI container in tests.

## Key Directories

| Path | Contents |
|------|----------|
| `src/main/java/io/casehub/blocks/attestation/` | Attestation write-path types — `AttestationIntent`, `AttestationIntentWriter` (+ `NoOpAttestationIntentWriter` `@DefaultBean`), `LifecycleAttestationObserver<E>` SPI, `AttestationContext` |
| `src/test/java/io/casehub/blocks/attestation/` | Tests for attestation types |
| `src/main/java/io/casehub/blocks/trust/` | Trust-lifecycle SPIs — `IntakeClassifier<S>`, `VouchService` with pluggable `VouchConstraint` chain |
| `src/test/java/io/casehub/blocks/trust/` | Tests for trust SPIs |
| `src/main/java/io/casehub/blocks/channel/` | Channel utility blocks — message meta, context tracking, bounded projection |
| `src/test/java/io/casehub/blocks/channel/` | Tests for channel blocks |
| `src/main/java/io/casehub/blocks/channel/summary/` | Channel summary integration — `ContentSummariser<Message>` implementations, `SummaryUpdateHook` adapter, `NoOpThreadSummaryStore` `@DefaultBean` |
| `src/test/java/io/casehub/blocks/channel/summary/` | Tests for channel summary integration |
| `src/main/java/io/casehub/blocks/agentic/` | Compositional agentic orchestration — five SPIs, execution drivers, pattern builders |
| `src/test/java/io/casehub/blocks/agentic/` | Tests for agentic orchestration blocks |
| `src/main/java/io/casehub/blocks/agentic/channel/` | Inter-agent channel setup + supervisor observation — `ChannelBinding`, `ChannelConfig`, `ChannelExecutionStrategy` (Conversation/FanIn/Barrier), `ChannelObserver<S>` (projection-based observation + EventSource) |
| `src/test/java/io/casehub/blocks/agentic/channel/` | Tests for agentic channel blocks |
| `src/main/java/io/casehub/blocks/agentic/aggregation/` | Aggregation strategies including `AuctionAggregation` — English/Dutch iterative auction via `AggregationStrategy<AuctionState>` with `BidExtractor` SPI |
| `src/test/java/io/casehub/blocks/agentic/aggregation/` | Tests for aggregation strategies |
| `src/main/java/io/casehub/blocks/agentic/coalition/` | Coalition formation — `CoalitionProposal`, `CoalitionEvaluator` SPI, `CapabilityCoverageEvaluator`, capability-based team assembly scoring |
| `src/test/java/io/casehub/blocks/agentic/coalition/` | Tests for coalition formation |
| `src/main/java/io/casehub/blocks/agentic/intention/` | Joint intentions — `JointIntention` lifecycle (form/activate/reconsider/drop/fulfill), `IntentionMonitor` SPI, `ReconsiderationSignal` |
| `src/test/java/io/casehub/blocks/agentic/intention/` | Tests for joint intentions |
| `src/main/java/io/casehub/blocks/agentic/belief/` | Formal belief revision — AGM-style `BeliefSet<T>` with expand/contract/revise, `ConsistencyChecker<T>` SPI, entrenchment ordering |
| `src/test/java/io/casehub/blocks/agentic/belief/` | Tests for belief revision |
| `src/main/java/io/casehub/blocks/agentic/social/` | Agent social cognition — `PersonalityEvolutionOrchestrator` (bounded trait drift via JPAF), `InnerLifeOrchestrator` (background thought loop with proactive initiation), `UserModelOrchestrator` (per-subject profile synthesis with tiered heuristic+LLM), `MentalModelOrchestrator` (per-actor BDI Theory of Mind with confidence decay, GOAP projection via `project()`, epistemic bridge via `observeConversation(CommonGroundState)`), `StrategyLearningOrchestrator` (multi-level reflection on interaction strategies with three-tier engagement analysis: heuristic counters, conversation case storage, LLM-backed periodic reflection via ReflectionOrchestrator + TrendAnalyzer), `MoodOrchestrator` (PAD emotional state with bounded decay toward personality baseline, mood-modulated retrieval support), `TraitPressureSource<E>` + `CivilityConstraint` + `InteractionSignal` + `MentalStateSignal` + `EngagementSignal` + `MoodSignal` SPIs, `UserProfileStore` SPI with `CbrUserProfileStore` `@DefaultBean`, `MentalModelStore` SPI with `CbrMentalModelStore` `@DefaultBean`, `StrategyStore` SPI with `CbrStrategyStore` `@DefaultBean`, `EvolutionTick`/`InnerLifeTick`/`UserModelTick`/`MentalModelTick`/`StrategyLearningTick`/`StrategyReflection`/`MoodTick` sealed outcomes, `UserProfile`/`StrategyProfile`/`MoodConfig`/`RelationshipStageConfig`/`StageTier`/`UserModelConfig`/`StrategyLearningConfig`/`AttributedState`/`MentalProjection`/`BdiDimension`/`CueType`/`MentalModelConfig`/`MentalModelSnapshot` types, default pressure sources and civility constraints |
| `src/test/java/io/casehub/blocks/agentic/social/` | Tests for social cognition |
| `src/main/java/io/casehub/blocks/agentic/social/goal/` | Autonomous goal proposal — `GoalProposalOrchestrator` (compositor), `DriveGoalMapper` SPI, four mapper implementations (Curiosity, Competence, Affiliation, Autonomy), `DriveGoalProposal`, `GoalProposalTick`, `GoalProposalConfig` |
| `src/test/java/io/casehub/blocks/agentic/social/goal/` | Tests for goal proposal |
| `src/main/java/io/casehub/blocks/agentic/social/narrative/` | Narrative identity — `NarrativeFragment` sealed hierarchy (`IndividualEpisode`, `GroupEpisode`, `DerivedTheme`), `NarrativeState` (scoped: INDIVIDUAL/GROUP), `NarrativeStore` SPI with `CbrNarrativeStore` `@DefaultBean` (CBR-backed via `NarrativeStateSchema` — polymorphic fragment serialization), `NarrativeOrchestrator` (compositor — reads NarrativeStore, caches NarrativeState per agent, `tick()` + `currentNarrative()`, per-agent ReentrantLock), `NarrativeSynthesiser` (`@ApplicationScoped` effectful — composite gate evaluation, LLM synthesis via AgentProvider, incremental episode merge + full theme re-derivation, capacity pruning, watermark-preserving reflection capping, degenerate response guard), `NarrativeModulation` (NarrativeState → drive modulation coefficients with per-axis clamping), `NarrativeConfig`/`NarrativeSynthesisGate` (composite trigger: count + novelty + quiet period), `NarrativeTick` (Updated with `@Nullable` previous)/`NarrativeSynthesisTick` sealed outcomes, `NarrativeScope` enum, `GroupNarrativeOrchestrator` (compositor — GROUP-scoped mirror of NarrativeOrchestrator, reads NarrativeStore with groupId, detects new GroupEpisodes/themes, exposes `members()` for consumer-provided group membership) |
| `src/test/java/io/casehub/blocks/agentic/social/narrative/` | Tests for narrative identity |
| `src/main/java/io/casehub/blocks/agentic/social/emergence/` | Social emergence — `SocialNorm` (detected behavioral pattern with `NormStrength`), `NormObservation` (interaction pattern for CBR storage), `DetectedNorms` (with `established()` filter), `NormDetectionConfig`/`NormDetectionTick`, `SocialNormDetector` (`@ApplicationScoped` compositor — reads NormObservation cases from CBR, groups by behavioralPattern, computes adherence rates, classifies strength EMERGING/ESTABLISHED/DECLINING, per-tenant caching), `NormObservationSchema` (CBR serialization), `DriveAlignment` (pairwise drive profile alignment), `CollectiveGoalProposal`/`CollectiveGoalTick`/`CollectiveGoalConfig`, `CollectiveGoalFormation` (compositor — reads N agents' DriveProfiles, computes pairwise alignment, forms groups via connected components, proposes collective goals when composite alignment exceeds threshold, per-tenant locking + per-group cooldown) |
| `src/test/java/io/casehub/blocks/agentic/social/emergence/` | Tests for social emergence |
| `src/main/java/io/casehub/blocks/agentic/personality/` | Personality evolution — signal-driven orchestrator composing eidos JPAF pipeline. `TraitPressureSource<E>` SPI, `PersonalityEvolutionOrchestrator` (tick/record), `EvolutionTick` sealed outcomes, default pressure sources (BehavioralSignal, RelationshipEvent, GoalOutcomeCounts) |
| `src/test/java/io/casehub/blocks/agentic/personality/` | Tests for personality evolution |
| `src/main/java/io/casehub/blocks/memory/` | Memory hygiene — `MemoryHygieneOrchestrator` (tick: importance scoring → eviction → consolidation), `MemoryHygieneScheduler` (maintain: tick + reflection + peer-linking + integrity), `ImportanceScorer` SPI with `ArousalScorer`/`SurpriseScorer`/`CompositeImportanceScorer`, `RetentionScore`/`RetentionConfig` (composite eviction scoring), `DefaultIntegrityChecker` (structural + semantic escalation), `ReflectionEntry`/`ReflectionStore` (reflection persistence SPI), `ReflectionQueryStore` SPI with `NoOpReflectionQueryStore` `@DefaultBean` (reflection read path for NarrativeSynthesiser), `HygieneTick`/`MaintenanceTick` sealed outcomes, `HygieneEvent` observability |
| `src/test/java/io/casehub/blocks/memory/` | Tests for memory hygiene |
| `src/main/java/io/casehub/blocks/conversation/` | Structured conversation protocol — projections, fold state, rendering, point classification, epistemic common ground, convergence detection |
| `src/test/java/io/casehub/blocks/conversation/` | Tests for conversation blocks |
| `src/main/java/io/casehub/blocks/conversation/orchestration/` | Conversation orchestrator — TurnPolicy SPI, termination conditions, PromptAssembler, ConversationOrchestrator composition root, ConversationListener per-dispatch callback |
| `src/test/java/io/casehub/blocks/conversation/orchestration/` | Tests for conversation orchestrator |
| `src/main/java/io/casehub/blocks/normative/` | Normative conflict resolution — `ConflictResolutionStrategy<T>` SPI, `NormDecision<T>`, `NormResolution<T>`, five resolution strategies (priority, specificity, recency, most-restrictive, escalation) |
| `src/test/java/io/casehub/blocks/normative/` | Tests for normative conflict resolution |
| `src/main/java/io/casehub/blocks/negotiation/` | Negotiation channel protocol — `NegotiationProjection`, `NegotiationFold`, `AcceptancePolicy` SPI (unanimous, majority, threshold), `NegotiationRenderer`, termination conditions (`MaxRoundsTermination`, `DeadlineTermination`, `AcceptedTermination`, `TerminalOutcomeTermination`, `NegotiationCompositeTermination`) |
| `src/test/java/io/casehub/blocks/negotiation/` | Tests for negotiation protocol |
| `src/main/java/io/casehub/blocks/oversight/` | Oversight gate lifecycle + risk classification — SPIs, classifier chaining, gate outcomes |
| `src/test/java/io/casehub/blocks/oversight/` | Tests for oversight blocks |
| `src/main/java/io/casehub/blocks/routing/` | Trust routing utilities — shared preference keys, policy resolver, compliance records |
| `src/test/java/io/casehub/blocks/routing/` | Tests for routing utilities |
| `src/main/java/io/casehub/blocks/routing/agent/` | AI-powered AgentRoutingStrategy implementations — LLM-reasoned and CBR-evidence agent selection, composable prompt enrichment pipeline, feature extraction SPI, outcome recording |
| `src/test/java/io/casehub/blocks/routing/agent/` | Tests for AI routing strategies |
| `src/main/java/io/casehub/blocks/prompt/` | DSPy-inspired prompt optimisation — core model, SPIs (`PromptOptimiser`, `PromptQualityMetric`, `PromptVariantStore`, `SystemPromptCustomiser`, `DiversityStrategy`), batch orchestration, A/B variant selection |
| `src/test/java/io/casehub/blocks/prompt/` | Tests for prompt optimisation framework |
| `src/main/java/io/casehub/blocks/prompt/optimiser/` | `PromptOptimiser` implementations — `FewShotOptimiser` (data-driven, diversity-aware via `DiversityStrategy`), `InstructionOptimiser` (LLM meta-prompting), `TopNDiversityStrategy` (identity), `OutcomeAwareDiversityStrategy` (MMR + Jaccard) |
| `src/test/java/io/casehub/blocks/prompt/optimiser/` | Tests for prompt optimisers |
| `src/main/java/io/casehub/blocks/prompt/runtime/` | CDI runtime beans — `OptimisedFewShotSection`, `VariantAwareSystemPromptCustomiser`, `WeightedOutcomeMetric`, `InMemoryPromptVariantStore` |
| `src/test/java/io/casehub/blocks/prompt/runtime/` | Tests for prompt runtime beans |
| `src/main/java/io/casehub/blocks/summarisation/` | Temporal abstraction framework + reusable content summarisation SPI — event levels, windowed accumulation, pluggable summarisation, `ContentSummariser<T>` with tiered dispatch |
| `src/test/java/io/casehub/blocks/summarisation/` | Tests for summarisation framework |
| `src/main/java/io/casehub/blocks/summarisation/llm/` | LLM-backed `ContentSummariser<T>` — generic synthesis via `AgentProvider` |
| `src/test/java/io/casehub/blocks/summarisation/llm/` | Tests for LLM content summariser |
| `src/main/java/io/casehub/blocks/summarisation/observation/` | Observation accumulator — tiered, demand-driven rendering for LLM agent prompts with RAG-able chunks |
| `src/test/java/io/casehub/blocks/summarisation/observation/` | Tests for observation accumulator |
| `src/main/java/io/casehub/blocks/summarisation/observation/affordance/` | Affordance grounding — per-entity observation rendering for LLM agents |
| `src/test/java/io/casehub/blocks/summarisation/observation/affordance/` | Tests for affordance rendering |
| `src/test/java/io/casehub/blocks/summarisation/examples/clinical/` | Clinical temporal abstraction example (L1-L4 pipeline) |
| `src/test/java/io/casehub/blocks/summarisation/examples/logistics/` | Logistics hub monitoring example (L1-L4 pipeline) |
| `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/` | sherpa-onnx FFM/Panama implementation — `SherpaOnnxSpeechToText` (+ `withDefaults()` zero-install factory), `SherpaOnnxTextToSpeech`, `VitsTextToSpeech` (direct onnxruntime + espeak-ng for TTS with exact phoneme timing), `SherpaLibrary` (native binding via FFM SymbolLookup, 3-tier loading: system path → local cache → auto-download), `OnnxRuntimeLibrary` (onnxruntime C API FFM vtable bindings, singleton env shared across sessions), `EspeakLibrary` (espeak-ng C API FFM bindings), `SherpaLayouts` (C struct layouts for sherpa-onnx 1.10.x), `Provisioner` (auto-download native lib + model with SHA-256 verification, `FileLock` concurrency, `tar xf` extraction; `ensureGectorModel()` for GECToR ONNX bundles), `ModelPatcher` (ONNX model duration output patching via protobuf), `VitsConfig` (Piper model config parsing + phoneme tokenization), `SentencePieceTokenizer` (pure Java Viterbi unigram segmentation — reads .model protobuf via CodedInputStream, trie-based vocab lookup), `GectorFilter` (`TextFilter` impl, destructiveness 3 — composes WordTokenizer + SentencePieceTokenizer + ORT + GectorTagApplier for iterative grammar correction), `GectorConfig` (tag vocabulary + verb dictionary + model path resolution from model directory), `GectorTagApplier` (stateless tag application — $KEEP/$DELETE/$APPEND/$REPLACE/morphological transforms), `WordTokenizer` (spaCy-compatible word pre-tokenization with contraction splitting), `WavReader`/`WavWriter` (PCM audio I/O), `SherpaConfig`, `SherpaException` |
| `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/` | Tests for sherpa-onnx implementation |
| `speech-ws/src/main/java/io/casehub/blocks/speech/ws/` | Avatar WebSocket endpoint — `SpeechWebSocket` (Quarkus WebSocket at `/ws/avatar`), `SpeechSession` (per-connection business logic), `VisemeMapping` (IPA→Oculus viseme), `PromptAssembler` SPI with `DefaultPromptAssembler`, `AvatarConfig` |
| `speech-ws/src/main/java/io/casehub/blocks/speech/ws/protocol/` | WebSocket protocol DTOs — `AvatarMessage` sealed hierarchy (Start, Stop, Partial, Transcript, Response, Phonemes, Error), `VisemeFrame`, `ConversationTurn`, `MessageCodec` (gson JSON encode/decode) |
| `speech-ws/src/test/java/io/casehub/blocks/speech/ws/` | Tests for avatar WebSocket (68 tests) |

## Package: `io.casehub.blocks.attestation`

Attestation write-path types and lifecycle observer SPI. `AttestationIntent` captures the full attestation payload; `AttestationIntentWriter` is the persistence SPI; `LifecycleAttestationObserver<E>` maps domain lifecycle events to attestation intents.

| Class | What it does |
|-------|-------------|
| `AttestationIntent` | Record: entryId, subjectId, verdict, confidence, capabilityTag, attestorId, actorType, attestorRole, dimensions (Map), evidence, namespace, causedByEntryId (nullable, for idempotent writes) |
| `AttestationIntentWriter` | SPI: `void write(AttestationIntent, String tenancyId)`. Implementations MUST honour the provided `entryId`. |
| `NoOpAttestationIntentWriter` | `@DefaultBean` `@ApplicationScoped` no-op implementation of `AttestationIntentWriter`. Consumers override with real persistence. |
| `LifecycleAttestationObserver<E>` | `@FunctionalInterface` SPI: `List<AttestationIntent> observe(E event, AttestationContext)`. Domain repos implement per event type. Returns non-null (empty list for irrelevant events). |
| `AttestationContext` | Record: tenancyId, caseId, capabilityTag — ambient context for observers |

## Package: `io.casehub.blocks.trust`

Trust-lifecycle SPIs with no compile-time attestation dependency. Intake classification and vouch orchestration.

| Class | What it does |
|-------|-------------|
| `IntakeClassifier<S>` | `@FunctionalInterface` SPI: `IntakeResult classify(S subject, IntakeContext)`. Generic subject — not coupled to trust infrastructure. |
| `IntakeContext` | Record: tenancyId, capabilityTag (nullable), attributes (Map escape hatch). Compact constructor defaults attributes to empty. |
| `IntakeResult` | Record: lane (String, domain-defined), confidence [0,1] (validated), reason, metadata. Compact constructor defaults metadata to empty. |
| `VouchConstraint` | SPI: `VouchEligibility check(VouchRequest)`. Pluggable eligibility check. |
| `VouchEligibility` | Sealed: `Eligible()`, `Ineligible(String reason)` |
| `VouchRequest` | Record: voucherId, voucheeId (UUID), capabilityTag, tenancyId, voucherActorType, voucherRole, namespace (nullable), attributes |
| `VouchResult` | Sealed: `Accepted(UUID attestationEntryId)`, `Rejected(List<String> reasons)` |
| `VouchService` | Orchestrator: runs all constraints (all-must-pass), writes ENDORSED attestation via `AttestationIntentWriter`. Not CDI-managed — consumer constructs with domain-specific constraints. |

## Package: `io.casehub.blocks.channel`

| Class | What it does |
|-------|-------------|
| `ChannelMessageMeta` | Sentinel-prefixed key=value metadata headers in message bodies. Apps choose their own sentinel. Methods: `parseMeta()`, `bodyContent()`, `encode()`, `parseInt()` |
| `ContextTracker` | Incremental LLM context window usage tracking via atomic counters. Thread-safe. |
| `ContextSnapshot` | Immutable record of context state: contribution chars, window size, effective %, threshold exceeded |
| `BoundedProjectionDecorator<S>` | Generic decorator wrapping any qhorus `ChannelProjection<S>` — skips messages past a configurable bound. Consumer supplies the value extraction function. |
| `ChannelAgentHandler` | SPI interface for sub-task handlers: `handles()`, `prepareTask()`, `buildResponse()`. First-match routing. |
| `ChannelAgentDispatcher` | First-match handler routing + agent invocation. Takes `Function<AgentTask, String>` (agent provider) and `Consumer<MessageDispatch>` (message sink). Subclass to override `onError()`. |
| `ChannelAgentRequest` | Record: channelId, correlationId, message (the sub-task trigger) |
| `AgentTask` | Record: systemPrompt, assembledInput (what to send to the LLM) |
| `AgentResultParseException` | Unchecked exception for handler parse failures |
| `ChannelEventAdapter<E>` | Direction 1 bridge: implements `MessageObserver`, extracts domain events via a `Function<MessageReceivedEvent, E>`, publishes `LevelEvent<E>` to an `EventStreamBus`. Null return from extractor filters. Extractor exceptions caught and logged. |
| `ChannelEventPublisher<E>` | Direction 2 bridge: subscribes to `EventStreamBus<E>`, converts events to `MessageDispatch` via a builder function, dispatches via `MessageDispatcher`. Best-effort — catches + logs, never propagates. |

## Package: `io.casehub.blocks.channel.summary`

Channel and thread summary integration — Message-specific `ContentSummariser<Message>` implementations, the `SummaryUpdateHook` adapter for channel summaries, and the push-based `ThreadSummaryObserver` for per-thread summaries.

| Class | What it does |
|-------|-------------|
| `HeuristicMessageSummariser` | `@DefaultBean` `ContentSummariser<Message>` — append-only structural summary from message metadata (participants, topics, time span). Merges annotations across invocations. Zero LLM cost. |
| `ChannelSummariser` | `@ApplicationScoped` `SummaryUpdateHook` adapter — delegates to injected `ContentSummariser<Message>`. Mutiny-aware blocking, channel-context error logging. |
| `NoOpThreadSummaryStore` | `@DefaultBean` `@ApplicationScoped` no-op `ThreadSummaryStore` — `save()` returns input, queries return empty. Consumers override with qhorus persistence. |
| `ThreadSummaryObserver` | `@ApplicationScoped` push-based observer — detects DONE/FAILURE messages with correlationId, fetches thread messages via `CrossTenantMessageStore`, delegates to `ContentSummariser<Message>`, writes to `ThreadSummaryStore`. Per-correlationId concurrency guard. Async via `ManagedExecutor`. |

## Package: `io.casehub.blocks.conversation`

Structured conversation protocol — reusable infrastructure for multi-agent deliberation channels. Extracted from drafthouse via casehubio/drafthouse#79, #80, #81, #83.

| Class | What it does |
|-------|-------------|
| `ConversationProtocol` | Sentinel-based metadata encoding/decoding for structured conversation messages. Defines entry types, round markers, status transitions. |
| `ConversationProjection` | Incremental projection over conversation messages — maintains fold state, tracks rounds, classifies points. |
| `ConversationFold` | Fold operations for typed-message projections — accumulates conversation state from a message stream. |
| `ConversationState` | Immutable snapshot of conversation state: points by thread, round boundaries, flags, sub-task status. |
| `ConversationPoint` | Individual point in a conversation thread — classification, priority, content, agent attribution. |
| `ConversationRenderer` | Pluggable markdown rendering of conversation state — round-by-round or thread-by-thread views. |
| `ConversationRendererConfig` | Configuration for renderer: section ordering, inclusion filters, format options. |
| `ThreadEntry` | Entry within a conversation thread — point + responses + sub-task findings. |
| `PointClassification` | Open type system for classifying conversation points (replaces drafthouse's closed `EntryType` enum). |
| `Priority` | Priority level for conversation points — used in rendering and attention ordering. |
| `RoundMemo` | Summary memo for a completed conversation round — key outcomes, unresolved points. |
| `FlagEntry` | Flag raised during conversation — attention markers for moderators or supervisors. |
| `SubTaskFinding` | Result from a sub-agent task (verify, analyse, etc.) attached to a conversation point. |
| `EpistemicStatus` | Enum: ESTABLISHED, PENDING, DISPUTED — classification outcome for common ground derivation. |
| `ParticipantContext` | Pre-computed participant tracking per point: allParticipants, respondedBy, acknowledgedBy, completedBy, disputedBy, failedBy, roundsSinceLastActivity. |
| `EpistemicRule` | `@FunctionalInterface` strategy for classifying conversation points by epistemic status. Composable via `and()` (conservative) / `or()` (permissive). |
| `EpistemicRules` | Three provided rules: `explicitAcknowledgement(minParticipants)`, `tacitAcceptance(windowRounds)`, `commitmentResolution()`. |
| `GroundedFact` | Epistemic metadata per point: pointId, topic, status, content, acknowledgedBy, disputedBy, round. |
| `CommonGroundState` | Derived view partitioning conversation points into establishedFacts, pendingClaims, disputedPoints. |
| `CommonGroundAnalyser` | Stateless utility: `analyse(ConversationState, EpistemicRule) → CommonGroundState`. Builds `ParticipantContext` per point and delegates classification to the rule. |
| `ConvergenceState` | Enum: PROGRESSING, CONVERGING, CONSENSUS, DEADLOCK, DIMINISHING_RETURNS. |
| `ConvergenceSignal` | Record: state, confidence (0.0–1.0), reason (human-readable). |
| `ConvergenceContext` | Pre-computed convergence indicators: totalPoints, established/pending/disputed counts, recentSimilarity, messageLengthTrend, roundsSinceNewPoint, roundsSinceStatusChange, recentMessageTypeCounts. |
| `ConvergencePolicy` | `@FunctionalInterface` strategy for evaluating convergence from conversation and common ground state. |
| `ConvergencePolicies` | Three provided policies: `structural(similarityThreshold, staleRounds)`, `commonGroundRatio(consensusThreshold, deadlockDisputeRatio)`, `composite(policies...)`. |
| `ConvergenceAnalyser` | Stateless utility: `analyse(ConversationState, CommonGroundState, ConvergencePolicy, recentWindow) → ConvergenceSignal`. |
| `RenderContext` | Supplementary render-time inputs: reactions, commonGround, convergence, progress. Replaces renderer overloads. |
| `ProgressRenderer` | `@FunctionalInterface` SPI: `String render(ProgressInstance)`. Pluggable progress-to-text rendering for LLM agent prompts. |
| `DefaultProgressRenderer` | Built-in `ProgressRenderer`: percentage (`"Label: N%"`), count (`"Label: N of M"`), step (`"a ✓ → b ⏳ → c ○"`), fallback to status name. Null-safe. |

## Package: `io.casehub.blocks.conversation.orchestration`

Autonomous multi-agent conversation orchestrator — composes `ConversationProjection`, `PartitionedObservationService`, pluggable turn policies, and `TerminationCondition` into a self-driving conversation loop.

| Class | What it does |
|-------|-------------|
| `ConversationOrchestrator` | Composition root — iterative queue-based loop. `converse(MessageView) → Uni<ConversationOutcome>`. Participants passed at construction; observation service auto-registers observers. `terminate()` for external stop (volatile flag). |
| `ConversationOutcome` | Record: `finalState`, `terminationDecision`, `agentResults`, `dispatchCount`, `elapsed` |
| `TurnPolicy` | SPI: `nextResponders(ConversationState, TurnContext, List<AgentParticipant>) → List<AgentParticipant>`. Synchronous. Empty list = silence. |
| `TurnContext` | Record: `senderId`, `@Nullable targetId`, `entryType`, `metadata`. Extracted from `MessageView` by orchestrator. |
| `AgentParticipant` | Record: `agentRef` (AgentRef), `role`, `systemPrompt`. `agentId()` delegates to `agentRef.name()`. |
| `PromptAssembler` | `@FunctionalInterface`: `assemble(AgentParticipant, PartitionedDrain<String>, ConversationState) → String`. Per-agent prompt construction. |
| `ResponseMessageBuilder` | `@FunctionalInterface`: `build(AgentParticipant, AgentResult, ConversationState) → MessageView`. Converts agent output to foldable message. |
| `ConversationListener` | `@FunctionalInterface`: `onDispatch(ConversationState, TerminationDecision, int, Duration)`. Per-dispatch callback fired after each termination check in `converse()`. Optional — wire via 10-arg constructor. |
| `RoundRobinTurnPolicy` | Strict alternation. Stateless — derives next from sender ID in participant order. |
| `AddressedTurnPolicy` | Respond when `targetId` matches agent role. Null target = silence. |
| `PointAddressedTurnPolicy` | Respond to unresolved OPEN/ACTIVE points not yet responded to by agent's role. |
| `FreeTurnPolicy` | All participants except sender. Pair with `MaxIterationsTermination`. |
| `AllAgreedTermination` | `TerminationCondition<ConversationState>` — Complete when all points have a resolved status (configurable set). |
| `SupervisorTermination` | `TerminationCondition<ConversationState>` — Complete when supervisor role signals end via configurable entry type. |
| `ContestedEscalation` | `TerminationCondition<ConversationState>` — Escalate when DISPUTED points exceed threshold. |
| `CompositeTermination` | `TerminationCondition<ConversationState>` — evaluates conditions in order; first non-Continue wins. |

## Package: `io.casehub.blocks.normative`

Generic normative conflict resolution — resolves conflicts between competing norm decisions from multiple sources. Speculative build (no consumer yet). Primary expected consumer: oversight pipeline composing `NormDecision<RiskDecision>`.

| Class | What it does |
|-------|-------------|
| `NormSpecificity` | Enum: UNIVERSAL, DOMAIN, TENANT, CASE_TYPE, INSTANCE. `isMoreSpecificThan()` for lex specialis comparison. |
| `ResolutionMethod` | Enum: PRIORITY, SPECIFICITY, RECENCY, MOST_RESTRICTIVE, ESCALATION. Identifies which strategy produced a resolution. |
| `NormDecision<T>` | Record: source, decision (generic T), priority, specificity, establishedAt. Wraps any decision with norm metadata. |
| `NormResolution<T>` | Record: winner (NormDecision), overridden (List), reason, method. Full audit trail of resolution outcome. |
| `ConflictResolutionStrategy<T>` | `@FunctionalInterface`: `NormResolution<T> resolve(List<NormDecision<T>>)`. Core SPI. |
| `PriorityResolution<T>` | Lowest priority int value wins. |
| `SpecificityResolution<T>` | Most specific norm wins (lex specialis). |
| `RecencyResolution<T>` | Most recently established norm wins (lex posterior). |
| `MostRestrictiveResolution` | Typed to `RiskDecision` — GateRequired beats Autonomous. Backward-compatible with ChainedActionRiskClassifier behavior. |
| `EscalationResolution<T>` | Always escalates to a designated decision when any conflict exists. |

## Package: `io.casehub.blocks.negotiation`

Negotiation channel protocol — reusable `ChannelProjection<NegotiationState>` for proposal/counter-proposal exchange. Supports bilateral (two-party alternating) and mediator-coordinated multilateral (N-party with configurable quorum). Uses PROPOSE MessageType (qhorus#395) for commissive speech acts. Party set required upfront for AcceptancePolicy correctness.

| Class | What it does |
|-------|-------------|
| `NegotiationOutcome` | Enum: PENDING, AGREED, DEADLOCKED, WITHDRAWN. `isTerminal()` for non-PENDING. |
| `ProposalStatus` | Enum: ACTIVE, SUPERSEDED, ACCEPTED, REJECTED. `isTerminal()` for ACCEPTED/REJECTED. |
| `PartyDecision` | Enum: ACCEPTED, REJECTED |
| `Proposal` | Record: proposalId, proposer, content, round (1-based), createdAt, status |
| `Response` | Record: party, decision, reason (@Nullable), respondedAt |
| `NegotiationState` | Record: proposals (ordered chain), parties, responses (per-party to active proposal), outcome. `activeProposal()`, `round()`, `hasActiveProposal()`. |
| `NegotiationFold` | Pure static state transitions: `propose()`, `accept()`, `reject()`, `agree()`, `deadlock()`, `withdraw()`. Counter-proposals supersede active proposal and clear responses. |
| `NegotiationProjection` | Concrete `ChannelProjection<NegotiationState>`. Constructor: `(Set<String> parties, AcceptancePolicy)`. Dispatches on PROPOSE/DONE/DECLINE. apply() never throws. Unknown senders ignored. |
| `AcceptancePolicy` | `@FunctionalInterface`: `boolean isAccepted(NegotiationState)`. Pluggable quorum evaluation. |
| `UnanimousAcceptance` | All non-proposer parties must accept |
| `MajorityAcceptance` | >50% of non-proposer parties must accept |
| `ThresholdAcceptance` | At least N acceptances required |
| `NegotiationRenderer` | Markdown rendering: current proposal, responses, pending parties, proposal history |
| `MaxRoundsTermination` | `TerminationCondition<NegotiationState>` — Complete at max rounds |
| `AcceptedTermination` | `TerminationCondition<NegotiationState>` — Complete when AGREED |
| `TerminalOutcomeTermination` | `TerminationCondition<NegotiationState>` — Complete for AGREED, Failed for DEADLOCKED/WITHDRAWN |
| `DeadlineTermination` | `TerminationCondition<NegotiationState>` — Complete when latest proposal exceeds deadline |
| `NegotiationCompositeTermination` | First-non-Continue-wins composition of termination conditions |
| `NegotiationProtocol` | Constants for negotiation outcome strings |

## Package: `io.casehub.blocks.oversight`

Oversight gate lifecycle and risk classification — SPIs for gating worker actions pending human approval. Extracted from engine-api via casehubio/engine (3cdb1f90) and casehubio/openclaw (37a7044).

| Class | What it does |
|-------|-------------|
| `ActionRiskClassifier` | Blocking SPI: classifies a worker's `PlannedAction` → `RiskDecision`. Annotate implementations with `@RiskClassifier @ApplicationScoped`. |
| `ReactiveActionRiskClassifier` | Reactive SPI: primary interface called by the engine. Consumers implement `ActionRiskClassifier` instead — the chain bridges blocking to reactive. |
| `RiskDecision` | Sealed interface (Autonomous, GateRequired). GateRequired carries reason, reversible flag, candidateGroups, expiresIn, scope. |
| `ClassificationContext` | Record: workerId, caseId, tenancyId, caseDefinitionName, capabilityName, bindingName. |
| `RiskClassifier` | CDI `@Qualifier` for `ActionRiskClassifier` implementations — prevents circular injection with the chain. |
| `ChainedReactiveActionRiskClassifier` | `@ApplicationScoped` CDI bean: discovers all `@RiskClassifier`-qualified classifiers, chains them, returns most-restrictive `RiskDecision`. Fail-safe: GateRequired on any exception. |
| `OversightGateService` | Blocking SPI: `openGate()` → `GateOutcome`, `fulfill()`. |
| `ReactiveOversightGateService` | Reactive SPI: `openGate()` → `Uni<GateOutcome>`, `fulfill()` → `Uni<Void>`. |
| `GateOutcome` | Sealed interface (Autonomous, GatePending). GatePending carries gateId + reason. |

## Package: `io.casehub.blocks.agentic`

Compositional agentic orchestration framework — eight sub-packages implementing five SPIs for routing, decomposition, activation, aggregation, and termination, plus execution drivers and pre-composed pattern builders.

| Sub-package | What it contains |
|-------------|-----------------|
| `agentic` | Foundation types: `AgentRef` (sealed: WorkerAgent, ChannelAgent, HumanAgent, ExternalAgent, ComposedAgent; extends `ExecutorRef` — each variant implements `name()`/`description()`), `AgentResult`, `RoutingCandidate`, `FailurePolicy`, `AgentCardSupport` (shared card-building via `ExecutorRef`; no more pattern matching). **Uni policy:** SPIs return direct types (not Uni) — virtual threads make blocking free. Only `AgentInvoker.invoke()` (concurrent dispatch) and API boundaries (`execute()`, `converse()`) keep Uni. |
| `agentic.routing` | Routing SPI: `RoutingStrategy<T>` (`RoutingDecision route(RoutingContext<T>)` — direct return, no Uni), `RoutingDecision` (sealed: Selected, Unresolvable, Escalate), `FirstMatchRouting`, `RoundRobinRouting`, `SequentialRouting`, `LlmSelectedRouting` |
| `agentic.decomposition` | Decomposition SPI: `DecompositionStrategy<T>`, `TaskNode` (sealed: LeafTask \| CompoundTask; LeafTask non-sealed: PrimitiveTask, PlannedTask; both carry `id`, `createdAt`, `status()` → PENDING, `executor()`, optional `OutputContract`), `DecompositionMethod`, `DecompositionContext`, `AgenticDecompositionContext` (blocks' `DecompositionContext<T>` impl — carries `agents`, `planningConstraints`; overrides `constraints()`; nullable fields default safely in compact constructor), `IdentityDecomposition`, `StaticDecomposition` (first-match guard evaluation; pre-filters methods whose `estimatedCost`/`estimatedDuration` exceed `context.constraints()` before guard evaluation), `ForwardReasoningDecomposition` (SHOP-style forward reasoning — applies `PrimitiveTask.effect()` to projected state during planning), `LlmDecomposition` (recursive multi-level planning via `maxDepth` — default 1 = flat; subtask entries become `CompoundTask` nodes recursively decomposed; depth-aware prompts; hierarchical context enrichment), `HybridDecomposition` (static→LLM fallback with `staticFailureHint`; `maxDepth` pass-through constructors), `CapabilityDependencyDecomposition` (GOAP backward-chaining), `HeuristicDecomposition` (ranked method selection via pluggable `DecompositionHeuristic<T>` with backtracking on `NoMethodMatchedException`; context-carried decomposer propagates heuristic through `SequenceStrategy` children), `DecompositionHeuristic<T>` (`@FunctionalInterface` SPI — batch-native async method scoring, higher-score-is-better), `ScoredMethod<T>` (record: method + score), `StructuralCostHeuristic` (zero-LLM tree-walking cost estimation), `LlmDecompositionHeuristic` (online LLM method evaluation via `AgentProvider`), `CompositeHeuristic` (weighted normalized combination of multiple heuristics with completeness contract validation), `Tasks` (DSL: `primitive()`, `planned()`, `compound()`, `decompose()` factories), `OutputContract` (`@FunctionalInterface` — `nonNull()`, `type()`, `of()`, composable via `and()`), `NoMethodMatchedException` (carries `methodCount`) |
| `agentic.activation` | Activation SPI: `ActivationRule<T>`, `ActivationContext`, `OnExplicitDispatch`, `MaxIterationsGuard` |
| `agentic.aggregation` | Aggregation SPI: `AggregationStrategy<T>`, `AggregationResult` (sealed: Resolved, Partial, Deadlocked), `PassThrough`, `CollectAll`, `MajorityVote`, `AuctionAggregation` (English/Dutch iterative auction — `Bid`, `AuctionState`, `AuctionOutcome`, `BidExtractor` SPI, `AuctionType`) |
| `agentic.channel` | Inter-agent channel infrastructure + supervisor observation: `ChannelBinding` (channelId + semantic), `ChannelConfig` (channelManager, dispatcher, semantic, protocols), `ChannelExecutionStrategy<T>` (sealed: Conversation, FanIn, Barrier), `ConversationChannelAdapter`, `DefaultConversationProjection`, `ChannelObserver<S>` (implements `MessageObserver` + `EventSource`; folds channel messages through `ChannelProjection<S>` via AtomicReference; `terminateWhen()`, `asTermination()` factory methods; builder for multi-channel observation), `ChannelTeardownListener` |
| `agentic.coalition` | Coalition formation: `CoalitionProposal`, `CoalitionEvaluator` SPI, `CoalitionScore`, `CoalitionContext`, `CapabilityCoverageEvaluator` |
| `agentic.intention` | Joint intentions (Bratman): `JointIntention` (form/activate/reconsider/drop/fulfill lifecycle), `IntentionMonitor` SPI, `ReconsiderationSignal`, `ReconsiderationReason`, `IntentionStatus` |
| `agentic.belief` | Formal belief revision (AGM): `BeliefSet<T>` (expand/contract/revise), `Belief<T>` (with entrenchment ordering), `ConsistencyChecker<T>` SPI |
| `agentic.termination` | Termination SPI: `TerminationCondition<T>` (composable via `or()`/`and()` with priority: Escalate > Failed > Complete > Continue), `TerminationDecision` (sealed: Continue, Complete, Failed, Escalate), `GoalReached`, `MaxIterationsTermination`, `JudgeConvergence`, `ConvergenceTermination` (bridges `ConvergencePolicy` → `TerminationCondition`) |
| `agentic.model` | Execution model: `ExecutionModel<T>` (carries `PatternType`), `ExecutionDriver<T>`, `AbstractExecutionDriver`, `OrchestratedDriver`, `ChoreographedDriver` (two modes: legacy continuous-loop, event-driven via `EventSource` + `EventConcurrencyPolicy` + `BlockingQueue<DriverEvent>`; `signal()` for direct push; `cancel()` posts cancellation event to break `queue.take()`), `DriverEvent` (typed event record: source, timestamp, payload), `EventSource` (`@FunctionalInterface` SPI for event delivery with `Cancellation`, `merge()`, `ticker()` factories), `EventConcurrencyPolicy` (pluggable policy: `serialize()`, `coalesce()`, `coalesceBySource()`, composable via `.then()`), `AgentInvoker<T>` (handles ExternalAgent + ComposedAgent; `withFallback()` for composition), `ExecutionBackend<T>` (pluggable execution — `reactive()` default via `CancellableBackend`; `choreographed(policy, sources...)` for event-driven; `cancel()` default method for external cancellation), `PatternType` (SEQUENCE/PARALLEL/LOOP/CONDITIONAL workflow-shaped; SUPERVISOR/DEBATE/VOTING/HTN custom-driver), `ExecutionResult` (sealed: Completed, Failed, Escalated, Cancelled), `ExecutionState` (sealed: Idle, Running, WaitingForAgent, WaitingForEvent, Complete, Faulted, Cancelled), `ExecutionEventListener` |
| `agentic.listener` | Accountability listeners: `OrchestrationEventType`, `EventLogListener` (operational audit via EventSink), `LedgerExecutionListener` (compliance audit via LedgerSink), `MetricsListener` (OTel metrics via Meter) |
| `agentic.pattern` | Pattern DSL: `Patterns` entry point, `AbstractPatternBuilder`, 8 builders (Supervisor, Sequence, Loop, Parallel, Voting, Debate, Conditional, HTN) |

## Package: `io.casehub.blocks.agentic.social.drive`

Drive Architecture — intrinsic motivation system that reads existing social cognition orchestrator outputs and synthesises motivational signals along four axes. Follows a *compositor pattern*: `tick()` without `record()`, since drives consume derived state rather than accumulating raw signals. Personality modulation via `AgentDescriptor.disposition()` (eidos); mood modulation via `MoodOrchestrator.currentMood()`.

| Class | What it does |
|-------|-------------|
| `DriveAxis` | Enum: CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY |
| `DriveIntensity` | Record: per-axis intensity [0,1] with human-readable trigger provenance |
| `DriveSource` | `@FunctionalInterface` SPI: `DriveIntensity evaluate(agentId, tenantId)`. Four implementations. |
| `DriveProfile` | Record: composite motivational state — per-axis intensities, weighted composite, dominant drive, timestamp |
| `DriveTick` | Sealed: `NoChange(reason)`, `Updated(previous, current, changed)` |
| `DriveConfig` | Record: per-axis weights, change threshold, modulation parameters, intensity bounds. `defaults()` factory. |
| `DriveOrchestrator` | `@ApplicationScoped` compositor lifecycle: `tick(agentId, tenantId, AgentDescriptor)` evaluates all sources, delegates to `DriveComposer`, caches `DriveProfile`. `currentDrives()` returns cached state. Per-agent `ReentrantLock`. CDI constructor takes `Instance<MemoryHygieneOrchestrator>` (optional), `Instance<NarrativeOrchestrator>` (optional — narrative modulation via `NarrativeModulation.compute()`), source orchestrators, `DriveComposer`, `DriveConfig`. Drive sources constructed internally. Wired into `InnerLifeOrchestrator.doTick()` for lifecycle tick. |
| `DriveComposer` | Modulation + composition algebra: mood modulation (PAD axes), personality modulation (disposition weight per axis), weighted composition, intensity clamping. Stateless. |
| `CuriosityDrive` | `DriveSource` — reads `MemoryHygieneOrchestrator.knowledgeGaps()`. Intensity from low-retention memory count + consolidation group diversity. |
| `CompetenceDrive` | `DriveSource` — reads `StrategyLearningOrchestrator.engagementTrend()`. Intensity from declining dimension ratio. |
| `AffiliationDrive` | `DriveSource` — reads `UserModelOrchestrator.activeProfiles()`. Intensity from neglected relationships (low familiarity or stale interaction). |
| `AutonomyDrive` | `DriveSource` — reads `MentalModelOrchestrator.activeSnapshots()`. Intensity from high-confidence intention count across subjects. |

## Package: `io.casehub.blocks.agentic.social.goal`

Autonomous goal proposal — Layer 2 of the autonomous intelligence stack. Translates intrinsic drive signals (from Drive Architecture, #129) into concrete `AgentGoal` proposals via per-axis heuristic mappers. Follows the *compositor pattern* (ADR-0001): `tick()` evaluates and caches, `currentProposals()` returns cached state, no side effects. The scheduler calls `GoalFormationService` (engine-api) separately to register proposals.

| Class | What it does |
|-------|-------------|
| `DriveGoalMapper` | `@FunctionalInterface` SPI: `@Nullable DriveGoalProposal evaluate(agentId, tenantId, DriveIntensity)`. Per-axis goal mapping. |
| `DriveGoalProposal` | Record: axis, goalName, goalDescription, formationReason, driveIntensity. Blocks-internal type. |
| `GoalProposalTick` | Sealed: `NoChange(reason)`, `Proposed(newProposals, abandonedGoalNames)` |
| `GoalProposalConfig` | Record: proposalThreshold, relevanceThreshold, maxDriveGoals, staleAfter, cooldown, failureAbandonmentThreshold. `defaults()` factory. |
| `GoalProposalOrchestrator` | `@ApplicationScoped` compositor: `tick(agentId, tenantId, AgentDescriptor)` evaluates mappers, ranks by intensity, checks capacity and cooldown. `currentProposals()` returns cached state. Per-agent `ReentrantLock`. |
| `CuriosityGoalMapper` | Reads `MemoryHygieneOrchestrator.knowledgeGaps()`. Proposes "explore-knowledge-gaps". |
| `CompetenceGoalMapper` | Reads `StrategyLearningOrchestrator.engagementTrend()`. Proposes "improve-{dimension}" for most declining. |
| `AffiliationGoalMapper` | Reads `UserModelOrchestrator.activeProfiles()`. Proposes "reconnect-{subjectId}" for most neglected. |
| `AutonomyGoalMapper` | Reads `MentalModelOrchestrator.activeSnapshots()`. Proposes "reassess-{subjectId}" for most misaligned. |

## Package: `io.casehub.blocks.routing`

Shared trust routing utilities — eliminates duplicated preference-to-policy boilerplate across domain repos.

| Class | What it does |
|-------|-------------|
| `DoublePreference` | `SingleValuePreference` record for double-typed preference values. Replaces copies in aml, devtown, life. |
| `IntPreference` | `SingleValuePreference` record for int-typed preference values. Replaces copies in aml, devtown. |
| `TrustRoutingPolicyKeys` | Parameterised `PreferenceKey` definitions — scope prefix + 4 universal keys (threshold, minimum-observations, borderline-margin, blend-factor) + builder for domain-specific quality floor keys. |
| `TrustRoutingPolicyResolver` | Stateless utility: `resolve(Preferences, TrustRoutingPolicyKeys)` → `TrustRoutingPolicy`. Also exposes `collectFloors()` for hybrid providers that read some fields from a domain registry. |
| `RoutingDecisionRecord` | Compliance audit record for trust-weighted routing decisions: capabilityTag, workerId, trustScoreAtRouting, thresholdApplied, evidenceEntryId. |
| `TrustRoutingRequirement` | Compliance evidence wrapper: requirementId, citation, mechanism, status, decisions. |
| `RequirementStatus` | Enum: CLOSED, PARTIAL, BREACHED, GAP. |

## Package: `io.casehub.blocks.routing.agent`

AI-powered `AgentRoutingStrategy` implementations for the engine's routing pipeline, plus composable prompt enrichment and outcome recording infrastructure. Strategies are selected by name via `StrategyResolver` (engine#634). Optional trust classification via `Instance<T>` — activates when engine-ledger is on the consumer's classpath.

| Class | What it does |
|-------|-------------|
| `LlmAgentRoutingStrategy` | `AgentRoutingStrategy` (id: `"llm"`). Asks an LLM via `AgentProvider` to reason about which candidate best fits the task. Delegates to `RoutingPromptAssembler` for composable prompt enrichment (CBR history, future signal sources). Optional trust classification. Worker pool offloading. |
| `CbrAgentRoutingStrategy` | `AgentRoutingStrategy` (id: `"cbr"`). Reads pre-retrieved experiences from `AgentRoutingContext.experiences()` and analyses worker success rates with configurable `CbrOutcomeWeights`, similarity-weighted scoring, and `RoutingSignalAssembler` integration. Falls back to `AgentGraphQuery.topAgentsByOutcome()` when CBR produces no match. Optional trust classification. |
| `CbrOutcomeWeights` | SPI for step-level routing outcome weights used by `CbrAgentRoutingStrategy`. Returns `Map<RoutingOutcome, Double>`. Domain repos override `DefaultCbrOutcomeWeights` with `@ApplicationScoped`. |
| `DefaultCbrOutcomeWeights` | `@DefaultBean` — SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, FAILURE=0.0. |
| `CbrCaseOutcomeWeights` | SPI for case-level outcome weights used by `PlanCompositionAnalyser`. Returns `Map<String, Double>` (string keys — case outcomes are domain-dependent). |
| `DefaultCbrCaseOutcomeWeights` | `@DefaultBean` — COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0. |
| `PlanCompositionAnalyser` | `RoutingSignalProvider` (id: `"plan-composition"`). Scores candidates based on case-level outcomes in multi-step plans (planTrace.size >= 2). Uses `CbrCaseOutcomeWeights` for case outcome weighting and similarity-weighted scoring. Returns null when no multi-step plan data exists. |
| `CbrRoutingPromptSection` | `RoutingPromptSection` implementation — formats historical CBR outcomes per eligible agent for LLM routing prompts. |
| `CoordinationSignalProvider` | `RoutingSignalProvider` (id: `"coordination"`). Scores candidates by historical team composition outcomes with adaptation-guided retrieval (AGR). Extracts team membership from experience plan traces, computes weighted mean of case-level outcomes where each candidate appeared in a multi-agent team, and weights by team re-assembly feasibility (`\|team ∩ candidatePool\| / \|team\|`). Uses `CoordinationOutcomeWeights` for case outcome weighting. |
| `CoordinationOutcomeWeights` | SPI for case-level outcome weights used by `CoordinationSignalProvider`. Returns `Map<String, Double>` (string keys — case outcomes are domain-dependent). Domain repos override `DefaultCoordinationOutcomeWeights` with `@ApplicationScoped`. |
| `DefaultCoordinationOutcomeWeights` | `@DefaultBean` — COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0. |
| `RoutingSupport` | Package-private utility — shared prompt building, response parsing, `AgentProvider` invocation, and trust classification extraction (`TrustFilterOutcome` sealed interface). Used by both `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy`. |
| `PredecessorAnalyser` | `RoutingSignalProvider` (id: `"predecessor"`). Scores candidates based on immediate predecessor context in historical plan traces — sorts steps by priority, finds steps matching target capability, scores by case outcome weighted by similarity with predecessor (capability:worker) pair in reason. |
| `DispositionAwareRouting` | `RoutingSignalProvider` (id: `"disposition"`). Scores candidates by personality/disposition match against a desired `DispositionProfile` extracted from case context (`_routing.disposition.<capabilityName>` or `default`). Exact-match scoring per `DispositionAxis` with optional per-axis weights. No-op when profile absent or no candidates have dispositions. |
| `DispositionProfile` | Record: `Map<DispositionAxis, String> desired` + `Map<DispositionAxis, Double> weights`. Compact value type for desired agent personality traits. |

## Package: `io.casehub.blocks.prompt`

DSPy-inspired prompt optimisation framework — offline batch cycle that auto-improves LLM routing and decomposition prompts based on CBR outcome feedback. Few-shot example curation, instruction refinement, deterministic A/B testing with circuit breaker safety rails. Pure Java core; CDI runtime beans in sub-packages.

| Class | What it does |
|-------|-------------|
| `PromptSignature` | Record: declares an optimisation target — id, description, base system prompt, I/O types. Passed to batch by consumers. |
| `PromptVariant` | Record: versioned optimisation bundle — examples, instruction delta, quality score, lineage pointer, consecutive wins. |
| `FewShotExample` | Record: curated example for prompt enrichment — input, output, outcome, quality score, annotation. |
| `VariantOutcome` | Record: outcome correlated with its variant — generic string outcome (not routing-specific). |
| `ExampleCandidate` | Record: pre-rendered case for example selection — batch constructs from `PlanCbrCase` data. |
| `OptimisationDataset` | Record: outcome summaries + full case data for example selection. |
| `OptimiserResult` | Record: output from a `PromptOptimiser` — examples + instruction delta. |
| `OptimiserConfig` | Record: batch configuration — maxExamples (5), minQualityThreshold (0.7), minOutcomeCount (50), minVariantOutcomes (20). |
| `SafetyConfig` | Record: safety rails — quality floor (0.3), max experiment cycles (5), max experiment age (30 days), circuit breaker threshold (5), master switch. |
| `BatchResult` | Sealed interface: AlreadyRunning, InsufficientData, NoImprovement, VariantCreated, VariantPromoted. |
| `PromptOptimiser` | SPI: teleprompter equivalent — `optimise(signature, currentVariant, dataset, config)`. Two implementations in `.optimiser` sub-package. |
| `PromptQualityMetric` | `@FunctionalInterface` SPI: scores variant performance from a list of `VariantOutcome`. |
| `PromptVariantStore` | SPI: holds active variants — store, getActive (by slot), getHistory, activate. |
| `SystemPromptCustomiser` | `@FunctionalInterface` SPI: customises base system prompts with instruction deltas from active variants. |
| `DiversityStrategy` | `@FunctionalInterface` SPI: `List<ExampleCandidate> select(shortlist, maxExamples)`. Pluggable re-ranking for `FewShotOptimiser`. Contract: subset of input, ≤ maxExamples, no mutation. |
| `VariantSelector` | Deterministic A/B split via `(hash & 0x7FFFFFFF) % 100`. Per-capability circuit breaker trips after consecutive experiment failures. |
| `PromptOptimisationBatch` | Batch orchestrator — gate check, score variants, run optimisers, build candidate, promotion decision (consecutive wins required), concurrency guard (per-signature lock). |

### Sub-package: `io.casehub.blocks.prompt.optimiser`

| Class | What it does |
|-------|-------------|
| `FewShotOptimiser` | `PromptOptimiser` (id: `"few-shot"`). Diversity-aware — filters by quality, scores by `qualityScore × similarityScore`, takes 2× shortlist, delegates to `DiversityStrategy` for final selection. No-arg constructor uses `TopNDiversityStrategy` (backward compatible). |
| `InstructionOptimiser` | `PromptOptimiser` (id: `"instruction"`). LLM meta-prompting — analyses outcome patterns, asks LLM to generate instruction refinements. Requires `AgentProvider`. Graceful degradation on LLM failure. |
| `TopNDiversityStrategy` | `DiversityStrategy` identity implementation — returns first N candidates from pre-sorted shortlist. Used as default. |
| `OutcomeAwareDiversityStrategy` | `DiversityStrategy` with outcome-category seeding + token-level Jaccard MMR. Constructor takes `diversityWeight` [0,1]. Skips seeding at 0.0 (pure relevance). Case-insensitive outcome grouping. |

### Sub-package: `io.casehub.blocks.prompt.runtime`

| Class | What it does |
|-------|-------------|
| `OptimisedFewShotSection` | `RoutingPromptSection` — injects curated few-shot examples from active variant into LLM routing prompts. Discovered via CDI alongside `CbrRoutingPromptSection`. |
| `VariantAwareSystemPromptCustomiser` | `SystemPromptCustomiser` `@DefaultBean` — reads instruction delta from active variant, appends to base system prompt. |
| `WeightedOutcomeMetric` | `PromptQualityMetric` `@DefaultBean` — maps outcome strings to weights (SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, DECLINED=0.0, FAILURE=0.0). Unknown outcomes default to 0.0. |
| `InMemoryPromptVariantStore` | `PromptVariantStore` `@DefaultBean` — in-memory with atomic write semantics (write-fsync-rename). |

## Package: `io.casehub.blocks.summarisation`

Temporal abstraction framework for summarising high-frequency event streams into progressively higher-level abstractions. Pure Java, zero CDI/Quarkus dependencies. Extracted from quarkmind via #27.

| Class | What it does |
|-------|-------------|
| `EventLevel` | Record: `(String name, int ordinal)` — identifies a level in the hierarchy |
| `LevelEvent<E>` | Record: `(E payload, long timestamp, EventLevel level)` — typed event at a specific level |
| `WindowPolicy` | Record: `(long maxAge, int maxCount)` — dual-trigger windowing. Validates: both >= 0, at least one positive. Factory methods: `ofCount(int)`, `ofAge(long)`, `of(long, int)`. |
| `EventAccumulator<E>` | Thread-safe event buffer. `collect()`, `shouldEmit(now)`, `drain()`, `drainIfReady(now)` (atomic check+drain), `clear()`, `size()`. Synchronized on all public methods. |
| `EventStreamBus<E>` | Predicate-based pub/sub. `subscribe(Predicate, Consumer)`, `publish(LevelEvent)` (synchronous dispatch on caller's thread), `clearSubscriptions()`. CopyOnWriteArrayList-backed — concurrent publish+subscribe safe. `clear()` deprecated. |
| `Summariser<IN, OUT>` | `@FunctionalInterface`. `CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>>)`. `ofSync()` factory for deterministic implementations. |
| `Compactor<E>` | `@FunctionalInterface`. `List<LevelEvent<E>> compact(List<LevelEvent<E>>)`. Optional pre-summarisation compaction (dedup, filtering, supersession). |
| `SummarisationRunner<IN, OUT>` | Wires accumulator → optional compactor → summariser → output bus. `collect()`, `tick()` (synchronized, returns `CompletionStage<Void>`), `clear()`, `size()`. Optional `Compactor<IN>` for pre-summarisation compaction. Optional `Consumer<List<LevelEvent<IN>>> onFailure` for failure recovery (default: log and drop). |
| `KeyedAccumulator<K, E>` | Groups events by key (via `Function<E, K>`), emits each group independently on completion predicate or stale timeout. Thread-safe. `collect()`, `drain(long now)`, `clear()`, `groupCount()`, `eventCount()`. Clock-from-last-event staleness semantics. |
| `KeyedSummarisationRunner<K, IN, OUT>` | Wires `KeyedAccumulator` → optional compactor → `Summariser` → output bus. Per-group failure recovery. `collect()`, `tick(long now)` (synchronized), `clear()`, `groupCount()`, `eventCount()`. Optional `Compactor<IN>` and `Consumer<List<LevelEvent<IN>>> onFailure`. |
| `SummaryMode` | Enum: `APPEND` (delta only) or `EDIT` (rewrite entire summary). Used by `LlmContentSummariser`. |
| `ContentSummariser<T>` | `@FunctionalInterface` SPI: `CompletionStage<SummaryResult> summarise(List<T>, @Nullable SummaryResult)`. Reusable batch summarisation — decoupled from pipeline event model. Returns `SummaryResult` (text + annotations). |
| `VerbatimContentSummariser<T>` | Renders each item as a bullet list via `Function<T, String>`. Preserves previous text and propagates annotations. |
| `TieredContentSummariser<T>` | Dispatches to delegates based on batch size thresholds. 2-tier `(small, large, threshold)` and 3-tier `(small, medium, large, t1, t2)` constructors. |
| `ContentSummariserToSummariser<T>` | Pipeline adapter: bridges `ContentSummariser<T>` → `Summariser<T, String>` for use in `SummarisationRunner`. Passes null previous (pipeline batches are independent). |

Two integration patterns: **Pattern A** (SummarisationRunner pipeline — sync heuristics, microsecond latency) and **Pattern B** (direct EventAccumulator — async LLM dispatch, caller manages). `KeyedSummarisationRunner` is the grouped counterpart to `SummarisationRunner` — same compositional role, groups by key instead of flat windowing. See spec for details.

## Sub-package: `io.casehub.blocks.summarisation.observation`

Terminal consumer of the summarisation pipeline — tiered, demand-driven rendering for LLM agent prompts with RAG-able chunk production.

| Class | What it does |
|-------|-------------|
| `ObservationTier` | Record: named tier in rendering hierarchy `(String name, int ordinal)`. Mirrors `EventLevel`. Predefined: VERBATIM, GROUPED, SUMMARISED. |
| `ObservationContext` | Record: render-time context `(long currentTime, long timeSinceLastDrain)`. |
| `ObservationChunk` | Record: RAG-able content unit with extensible `Map<String, String>` metadata. Agent-agnostic — consumer adds identity when storing. |
| `ObservationResult` | Record: drain output — `renderedText` + `chunks` + metadata. Factory: `empty(timeSinceLastDrain)`. |
| `ObservationRenderer<E>` | `@FunctionalInterface` SPI: `render(List<LevelEvent<E>>, ObservationContext) → CompletionStage<ObservationResult>`. Stateless and shareable. |
| `TieredObservationRenderer<E>` | Standard implementation: routes to verbatim (≤ threshold), grouped (≤ threshold), or summarised (via `Summariser<E, String>`) based on batch size. Configurable header via `withHeaderFormatter`. Two-tier and three-tier constructors. |
| `ObservationAccumulator<E>` | Thread-safe buffer with demand-driven drain. Own buffer (not `EventAccumulator`). Tracks `lastDrainTimestamp`. Empty drain → `ObservationResult.empty()`. At-most-once delivery on renderer failure. |

## Sub-package: `io.casehub.blocks.summarisation.observation.affordance`

Grounded observation rendering for LLM agents. Per-entity affordance chains (identity + action + consequence) and typed section assembly. Parallel producer to the temporal observation pipeline — consumer concatenates both.

| Class | What it does |
|-------|-------------|
| `ObservableEntity` | Record: entity visible to an agent `(String id, String displayName, @Nullable String description, List<Affordance> affordances)` |
| `Affordance` | Record: action available on an entity `(String actionType, @Nullable String label, @Nullable String requiredItem, List<String> acceptsItems)`. Compositional tag format: `[ACTION label, requires: item, with: items]` |
| `ObservationSection` | Sealed interface: `EntityGroup` (entities with grounding chains), `TextBlock` (contextual prose), `ItemList` (bulleted items). Factory methods: `entities()`, `text()`, `items()` |
| `ActionDescriptor` | Record: action type in the vocabulary `(String actionType, String description, @Nullable String parameterFormat)` |
| `AffordanceRenderer` | Concrete class: `renderEntities()` (core grounding chains), `renderObservation()` (section assembly), `renderActionVocabulary()` (action vocabulary). Configurable header formatter via `withHeaderFormatter()` |
| `WorldObservationProvider` | `@FunctionalInterface` SPI: `List<ObservationSection> worldSections()`. Returns world-specific observation sections (location, exits, objects, characters). Consumers accept a provider instead of a concrete world state, so cognitive sections (goals, plans, memories) become a shared utility. |
| `CognitiveObservationSections` | Static utility: factory methods for cognitive observation sections — `goalsSection(List<AgentGoal>)`, `recentActivitySection(PartitionedDrain)`, `pastExperienceSection(List<Memory>)`, `insightsSection(List<Memory>)`, `relationshipNotesSection(String, List<Memory>)`, `motivationalStateSection(DriveProfile)`. Each returns an `ObservationSection`. |

## Sub-package: `io.casehub.blocks.summarisation.llm`

LLM-backed content summarisation. Separated from the pure-Java `blocks.summarisation` package because it depends on `AgentProvider` (platform-agent-api) and Mutiny.

| Class | What it does |
|-------|-------------|
| `LlmContentSummariser<T>` | Generic `ContentSummariser<T>` backed by `AgentProvider`. EDIT/APPEND modes via `SummaryMode`. Optional `preamble` for static context (e.g., channel name). Propagates previous annotations. |

## Package: `io.casehub.blocks.speech.sherpa`

sherpa-onnx speech implementation via Java FFM/Panama (JDK 22+). Provides `SpeechToTextService` and `TextToSpeechService` implementations backed by sherpa-onnx native library. Zero foundation dependencies — only depends on `casehub-blocks-speech-api`.

| Class | What it does |
|-------|-------------|
| `SherpaOnnxSpeechToText` | `SpeechToTextService` implementation — Whisper-based transcription via FFM downcalls. Reads WAV files, feeds PCM samples to sherpa-onnx offline recognizer, returns `TranscriptionResult`. |
| `SherpaOnnxTextToSpeech` | `TextToSpeechService` implementation — VITS/Piper-based synthesis via FFM downcalls. Generates audio from text, encodes to WAV, returns `SynthesisResult`. Auto-detects TTS model filename via `findTtsModel()` directory scan. |
| `SherpaLibrary` | Native library loading via `SymbolLookup.libraryLookup()`. Resolves all STT and TTS function handles. Singleton with explicit path override. `isAvailable()` for runtime detection. |
| `SherpaLayouts` | `StructLayout` definitions for sherpa-onnx 1.10.x C API structs — `OFFLINE_RECOGNIZER_CONFIG`, `TTS_CONFIG`, `GENERATED_AUDIO`. VarHandles for field access. |
| `SherpaConfig` | Record: `modelDir`, `numThreads`, `provider`. `defaults(Path)` factory. |
| `WavData` | Record: `float[] samples`, `sampleRate`, `channels` — decoded PCM audio. |
| `WavReader` | WAV file parser — reads RIFF/WAV headers, extracts 16-bit PCM samples as float[]. |
| `WavWriter` | WAV encoder — encodes float[] samples to 16-bit PCM WAV bytes. |
| `MicrophoneCapture` | Audio capture utility — bridges `javax.sound.sampled.TargetDataLine` to `RecognitionStream.acceptSamples()`. Background platform daemon thread reads 100ms PCM chunks. Injectable `TargetDataLine` for testability, `openDefault()` factory for convenience. |
| `SpeechCli` | CLI entry point — `transcribe` (offline Whisper), `synthesise` (VITS/Piper), `stream` (streaming Zipformer from WAV), `listen` (live microphone streaming STT). |
| `VitsTextToSpeech` | `TextToSpeechService` implementation — direct onnxruntime + espeak-ng TTS with exact per-phoneme timing from VITS duration predictor. Bypasses sherpa-onnx TTS API. Composes `EspeakLibrary` (phonemization) + `VitsConfig` (tokenization with VITS blank interspersing) + `OnnxRuntimeLibrary` (inference). `AutoCloseable` — owns ORT session lifecycle. Graceful degradation for unpatched models. |
| `OnnxRuntimeLibrary` | FFM bindings for onnxruntime C API via vtable pattern (`OrtGetApiBase`). Loads `libonnxruntime` independently (system path → sherpa-onnx cache). Singleton with shared ORT env across sessions. `Session` inner class wraps ORT session with typed inference (`runFloat`, `runRaw`), tensor creation, output count/name queries. |
| `EspeakLibrary` | FFM bindings for espeak-ng C API — `espeak_Initialize`, `espeak_SetVoiceByName`, `espeak_TextToPhonemes`, `espeak_Terminate`. All methods `synchronized` (espeak-ng uses global state). Singleton. |
| `VitsConfig` | Piper VITS model config — parses `.onnx.json` via gson for `phoneme_id_map`, `espeak.voice`, `audio.sample_rate`, inference defaults. Greedy longest-match IPA tokenization with VITS blank token interspersing (2N+1 output). Reverse phoneme ID mapping for duration-to-phoneme conversion. |
| `ModelPatcher` | Static utility — patches ONNX model to expose `/Ceil_output_0` (VITS duration predictor output) as a second model output. Uses protobuf-java `UnknownFieldSet` for schema-free ONNX manipulation. Idempotent. |
| `SentencePieceTokenizer` | Pure Java SentencePiece tokenizer — Viterbi unigram segmentation. Reads `.model` protobuf via `CodedInputStream` (no proto compilation). Trie-based vocabulary lookup, space normalization (▁), consecutive unknown collapsing. `encode(String) → int[]`, `decode(int[]) → String`. |
| `GectorFilter` | `TextFilter` implementation (destructiveness 3, name "grammar"). Composes `WordTokenizer` + `SentencePieceTokenizer` + `OnnxRuntimeLibrary` + `GectorTagApplier` for iterative GECToR grammar correction. Per-word subword encoding, DeBERTa inference, softmax + threshold filtering, first-subword-wins tag aggregation. `AutoCloseable` — owns ORT session. |
| `GectorConfig` | Record: `modelPath`, `spModelPath`, `maxIterations` (default 5), `keepConfidence`, `minErrorProb`, `numThreads`, `provider`, `tagVocabulary`, `keepTagId`, `verbDictionary`. Factory `fromModelDir(Path)` reads `labels.txt` and optional `verb-form-vocab.txt`. |
| `GectorTagApplier` | Stateless utility: `apply(List<String> tokens, int[] tagIds, GectorConfig) → Result(tokens, changed)`. Handles $KEEP, $DELETE, $APPEND_{word}, $REPLACE_{word}, $TRANSFORM_VERB/CASE/AGREEMENT, $MERGE_HYPHEN. Verb transforms fall back to original when not in dictionary. |
| `WordTokenizer` | Rule-based word pre-tokenization matching spaCy English rules. Contraction splitting (n't, 's, 're, 've, 'll, 'd, 'm), punctuation separation. `splitSentences(String)` for sentence boundary detection. |
| `SherpaException` | Unchecked exception for native binding failures. |

## Dependencies

**Compile:** `casehub-qhorus-api`, `casehub-work-api`, `casehub-engine-api`, `casehub-eidos-api`, `casehub-worker-api`, `org.jspecify:jspecify`
**Provided:** `io.smallrye.reactive:mutiny`, `casehub-platform-agent-api`, `casehub-platform-api`, `casehub-engine-ledger`, `casehub-ledger-api`, `casehub-neocortex-memory-api`, `casehub-work-progress-api`, `io.opentelemetry:opentelemetry-api`
**Test:** `casehub-qhorus`, `casehub-qhorus-testing`, `casehub-engine`, `casehub-engine-testing`, `assertj`, `mockito`, `awaitility`, `io.opentelemetry:opentelemetry-sdk-testing`

**No Jandex index.** blocks does not include a Jandex index — its CDI beans are not auto-discovered by Quarkus. Consumers that need blocks' CDI beans (routing strategies, channel summarisers) must opt in:
```properties
quarkus.index-dependency.casehub-blocks.group-id=io.casehub
quarkus.index-dependency.casehub-blocks.artifact-id=casehub-blocks
```
Consumers that only use blocks' pure types (records, sealed interfaces, plain classes) need no configuration.

**speech-sherpa module:**
**Compile:** `casehub-blocks-speech-api`, `org.jspecify:jspecify`, `com.google.code.gson:gson`, `com.google.protobuf:protobuf-java`
**Runtime:** sherpa-onnx native library (`sherpa-onnx-c-api`) — must be on `java.library.path`; `libonnxruntime` (bundled with sherpa-onnx); `libespeak-ng` (optional, for `VitsTextToSpeech` phoneme timing)
**Compiler target:** Java 22 (stable FFM/Panama API)
**Test:** JUnit 5, AssertJ

**speech-ws module:**
**Compile:** `casehub-blocks-speech-api`, `com.google.code.gson:gson`, `org.jspecify:jspecify`
**Provided:** `casehub-platform-agent-api`, `quarkus-websockets-next`
**Compiler target:** Java 17
**Test:** JUnit 5, AssertJ, Mockito

## Consumers

| Repo | What it uses |
|------|-------------|
| casehub-drafthouse | Channel + conversation blocks — DebateProtocol delegates to `ConversationProtocol`, DebateChannelProjection extends `ConversationProjection`, ReviewChannelProjection uses `ConversationFold`/`ConversationState`, `ChannelAgentDispatcher` subclass with debate-specific error dispatch, `BoundedProjectionDecorator` for round bounding, `ContextTracker` for LLM window tracking |
| casehub-engine | Oversight: `GateOutcome`, `OversightGateService`, `ReactiveOversightGateService` (NoOp impls), `ReactiveActionRiskClassifier`, `RiskDecision`, `ClassificationContext` (handler + health check) |
| casehub-openclaw | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext`, `GateOutcome` (concrete OversightGateService impl) |
| casehub-aml | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver`, `DoublePreference`, `IntPreference`. Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-devtown | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`, `DoublePreference`. Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-life | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`, `DoublePreference`. Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-soc | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-clinical | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-iot | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-quarkmind | Summarisation: `SummarisationRunner`, `EventStreamBus`, `EventAccumulator`, `WindowPolicy`, `Summariser`, `EventLevel`, `LevelEvent` — SC2 game event hierarchy (L2→L3→L4 pipeline via `SummarisationLifecycle`, `GamePhaseSummariser`, `GameArcSummariser`) |

## Blocks Scope Criteria

A pattern belongs in blocks if it meets at least one of these criteria:
1. **Needs an LLM in the loop** — the pattern involves LLM invocation, prompt construction, or LLM-driven decision-making
2. **Uses classical AI** — classical planning, Bayesian reasoning, CEP (complex event processing), or similar
3. **Requires integration with foundational platform parts** — the pattern composes across qhorus, engine, work, or eidos APIs in a way that would otherwise be duplicated by every consumer

**What does NOT belong in blocks:**
- Small isolated utilities (backoff computation, rate limiters, CloudEvent adapters) → stay in platform or engine
- Pure SPI unifications (e.g. ProvisionerConfigRegistry) → stay in the API module that owns the provisioning lifecycle (engine-api)
- Domain-specific logic that happens to be duplicated but doesn't involve AI or foundational integration

**The test:** if removing the LLM/AI/integration aspect leaves a generic utility, it belongs in platform. If removing the domain-specific aspect leaves a reusable AI-integration pattern, it belongs in blocks.

## Trust Routing Architecture

The trust routing system spans four layers — blocks owns policy configuration AND AI-powered routing strategies.

| Layer | Owner | What it does |
|-------|-------|-------------|
| Score computation | **ledger** | `TrustScoreRoutingPublisher` computes trust scores from ledger entries and publishes them. The `trust-score-routing` package owns all score payloads and events. |
| Policy configuration | **blocks** (routing package) + **engine-api** (`TrustRoutingPolicyProvider` SPI) | `TrustRoutingPolicyKeys` + `TrustRoutingPolicyResolver` provide the shared preference-to-policy loading. Domain repos implement `TrustRoutingPolicyProvider` using these utilities. |
| Classical strategy execution | **engine** | `TrustWeightedAgentStrategy` (engine-ledger) applies trust scores. `SemanticAgentRoutingStrategy` (engine-ai) adds embedding-based re-ranking. Strategies stay where their differentiating dependency lives. |
| AI-powered strategy execution | **blocks** (routing.agent package) | `LlmAgentRoutingStrategy` (LLM reasoning) and `CbrAgentRoutingStrategy` (case-based evidence). Both optionally compose with trust classification via `Instance<TrustCandidateClassifier>`. |

Domain repos (aml, devtown, clinical, life, ops) implement `TrustRoutingPolicyProvider` from engine-api — they configure policy parameters, not compute scores or execute routing.

## Consolidation Epic

Epic #28 tracks extraction of shared patterns from domain repos into blocks. Each child issue covers a distinct pattern duplicated across 2+ repos.

| # | Title | Scale | Complexity | Ready? | Destination | Migrates from | Downstream consumers |
|---|-------|-------|------------|--------|-------------|---------------|---------------------|
| #17 | Trust routing YAML | M | Med | **Done** | blocks | aml, devtown, clinical, life, ops, soc | aml, devtown, clinical, life, ops, soc, fsitrading |
| #22 | Debate channel infrastructure | L | High | **Done** | blocks | drafthouse | drafthouse, devtown, clinical, aml, claudony |
| #23 | Oversight gate lifecycle + risk classification | L | High | **Done** | blocks | openclaw, engine-api | openclaw, aml, soc, life, devtown, clinical, iot, claudony |
| #24 | Universal pluggable routing strategy | L | High | **Moved → engine#634** | engine | engine, work | engine, work, qhorus, eidos |
| #30 | AI routing strategy impls (trust, LLM, CBR) | M | Med | **Done** | blocks | — | engine, domain repos |
| #25 | Worker data coordination (DataExchange/DataChannel) | L | High | **Moved → engine#633** | engine | engine | engine, workers, desiredstate |
| #27 | Layered event summarisation | M | Med | **Done** | blocks | quarkmind | quarkmind, iot, aml, clinical |

## Cross-Repo Scanning

To scan all CaseHub repos for shared patterns, use `ide_open_workspace` with the parent directory:
```
ide_open_workspace(path="/Users/mdproctor/claude/casehub")
```
This opens all 26 repos in a single IntelliJ window with full cross-project code intelligence. Use `ide_find_class`, `ide_search_text`, and `ide_find_references` with the workspace `project_path` for cross-repo analysis.

## Cross-Repo Consolidation Commits

When implementing consolidation work (epic #28), commits to peer repos (aml, devtown, life, etc.) on main are expected and approved. This is an exception to the normal "do not commit to peer repos" rule — consolidation by definition spans repos. Always:
- Verify all affected repos are on main before starting
- Install blocks to local Maven repo before compiling consumers (`mvn install -DskipTests`)
- Commit each repo separately with meaningful messages tagged with the blocks issue (`Refs casehubio/blocks#N`)
- Push all repos after all commits succeed

## Extraction Plan

Full extraction plan with prioritisation: [casehubio/parent#310 comment](https://github.com/casehubio/parent/issues/310#issuecomment-4795440229). P1–P5 complete. Consolidation epic #28 tracks remaining extractions.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

## Project Artifacts

Paths that are project content (not workspace noise). Skills use this to avoid
filtering or dropping commits that touch these paths.

| Path | What it is |
|------|------------|
| `CLAUDE.md` | Project conventions |
| `docs/` | Documentation |

## IntelliJ MCP Routing

One IntelliJ MCP server is available:

- **`mcp__intellij-index__*`** — use this for ALL code intelligence and navigation. Supports auto-opening projects via `project_path` — pass the project path and the plugin opens it automatically. Never ask the user to open a project manually.

`mcp__intellij__*` (built-in JetBrains MCP) is **disabled** due to a memory leak. Do not attempt to use it.

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
For all Java work: `java-dev`
Before committing: `superpowers:requesting-code-review`
After implementation: `implementation-doc-sync`

## Work Tracking

Issue tracking: enabled
GitHub repo: casehubio/blocks
Changelog: GitHub Releases
